package com.rumal.admin_service.auth;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

@Service
public class KeycloakVendorAdminManagementService {

    private final String serverUrl;
    private final String realm;
    private final String adminRealm;
    private final String clientId;
    private final String clientSecret;
    private final String vendorAdminRoleName;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;

    public KeycloakVendorAdminManagementService(
            @Value("${keycloak.issuer-uri}") String issuerUri,
            @Value("${keycloak.realm}") String realm,
            @Value("${keycloak.admin.realm:${keycloak.realm}}") String adminRealm,
            @Value("${keycloak.admin.client-id}") String clientId,
            @Value("${keycloak.admin.client-secret}") String clientSecret,
            @Value("${keycloak.vendor-admin-role:vendor_admin}") String vendorAdminRoleName,
            CircuitBreakerFactory<?, ?> circuitBreakerFactory
    ) {
        this.serverUrl = resolveBaseUrlFromIssuer(issuerUri);
        this.realm = realm;
        this.adminRealm = adminRealm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.vendorAdminRoleName = vendorAdminRoleName;
        this.circuitBreakerFactory = circuitBreakerFactory;
        if (!StringUtils.hasText(this.clientId) || !StringUtils.hasText(this.clientSecret)) {
            throw new IllegalStateException("KEYCLOAK_ADMIN_CLIENT_ID and KEYCLOAK_ADMIN_CLIENT_SECRET must be configured");
        }
    }

    public KeycloakManagedUser ensureVendorAdminUser(
            String requestedKeycloakUserId,
            String email,
            String firstName,
            String lastName,
            boolean createIfMissing
    ) {
        String normalizedEmail = normalizeEmail(email);
        return runKeycloakCall(() -> {
            try {
                Keycloak keycloak = getOrCreateAdminClient();
                var realmResource = keycloak.realm(realm);
                UserRepresentation user = null;
                boolean created = false;

                if (StringUtils.hasText(requestedKeycloakUserId)) {
                    try {
                        user = realmResource.users().get(requestedKeycloakUserId.trim()).toRepresentation();
                    } catch (NotFoundException ex) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Keycloak user not found: " + requestedKeycloakUserId);
                    }
                    if (user == null || !StringUtils.hasText(user.getId())) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Keycloak user not found: " + requestedKeycloakUserId);
                    }
                    String existingEmail = resolveEmail(user);
                    if (StringUtils.hasText(existingEmail) && !normalizedEmail.equalsIgnoreCase(existingEmail)) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "Provided email does not match Keycloak user email for id " + requestedKeycloakUserId
                        );
                    }
                } else {
                    user = findUserByEmail(realmResource.users().searchByEmail(normalizedEmail, true), normalizedEmail);
                    if (user == null && createIfMissing) {
                        user = createUser(realmResource, normalizedEmail, firstName, lastName);
                        created = true;
                    }
                    if (user == null) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Keycloak user not found for email: " + normalizedEmail);
                    }
                }

                updateNamesIfProvided(realmResource, user, firstName, lastName);
                assignRealmRoleIfMissing(realmResource, user.getId(), vendorAdminRoleName);
                boolean actionEmailSent = false;
                if (created) {
                    sendRequiredActionsEmail(realmResource, user.getId());
                    actionEmailSent = true;
                }

                UserRepresentation refreshed = realmResource.users().get(user.getId()).toRepresentation();
                return new KeycloakManagedUser(
                        refreshed.getId(),
                        resolveEmail(refreshed),
                        refreshed.getFirstName(),
                        refreshed.getLastName(),
                        created,
                        actionEmailSent
                );
            } catch (ResponseStatusException ex) {
                throw ex;
            } catch (WebApplicationException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Keycloak admin request failed: " + ex.getMessage(), ex);
            } catch (Exception ex) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Keycloak admin operation failed", ex);
            }
        });
    }

    public List<KeycloakUserSearchResult> searchUsers(String query, int limit) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "q is required");
        }

        int safeLimit = Math.max(1, Math.min(limit, 20));

        return runKeycloakCall(() -> {
            try {
                Keycloak keycloak = getOrCreateAdminClient();
                var realmResource = keycloak.realm(realm);
                var usersResource = realmResource.users();

                Map<String, UserRepresentation> unique = new LinkedHashMap<>();
                collectUsers(unique, usersResource.search(normalizedQuery, 0, safeLimit), safeLimit);
                if (normalizedQuery.contains("@")) {
                    collectUsers(unique, usersResource.searchByEmail(normalizedQuery, false), safeLimit);
                }

                List<KeycloakUserSearchResult> results = new ArrayList<>();
                for (UserRepresentation user : unique.values()) {
                    if (!StringUtils.hasText(user.getId())) {
                        continue;
                    }
                    results.add(new KeycloakUserSearchResult(
                            user.getId(),
                            resolveEmail(user),
                            normalizeOptional(user.getUsername()),
                            normalizeOptional(user.getFirstName()),
                            normalizeOptional(user.getLastName()),
                            composeDisplayName(user),
                            Boolean.TRUE.equals(user.isEnabled()),
                            Boolean.TRUE.equals(user.isEmailVerified())
                    ));
                    if (results.size() >= safeLimit) {
                        break;
                    }
                }
                return List.copyOf(results);
            } catch (ResponseStatusException ex) {
                throw ex;
            } catch (WebApplicationException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Keycloak user search failed: " + ex.getMessage(), ex);
            } catch (Exception ex) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Keycloak user search failed", ex);
            }
        });
    }

    public void logoutUserSessions(String keycloakUserId) {
        if (!StringUtils.hasText(keycloakUserId)) {
            return;
        }
        runKeycloakVoid(() -> {
            try {
                Keycloak keycloak = getOrCreateAdminClient();
                try {
                    keycloak.realm(realm).users().get(keycloakUserId.trim()).logout();
                } catch (NotFoundException ignored) {
                    // User already removed in Keycloak; nothing to revoke.
                }
            } catch (WebApplicationException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Keycloak session logout failed", ex);
            } catch (Exception ex) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Keycloak session logout failed", ex);
            }
        });
    }

    public void logoutUserSessionsBulk(Collection<String> keycloakUserIds) {
        if (keycloakUserIds == null || keycloakUserIds.isEmpty()) {
            return;
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String id : keycloakUserIds) {
            if (StringUtils.hasText(id)) {
                unique.add(id.trim());
            }
        }
        if (unique.isEmpty()) {
            return;
        }
        runKeycloakVoid(() -> {
            try {
                Keycloak keycloak = getOrCreateAdminClient();
                var users = keycloak.realm(realm).users();
                for (String id : unique) {
                    try {
                        users.get(id).logout();
                    } catch (NotFoundException ignored) {
                        // Ignore orphaned/local stale rows.
                    }
                }
            } catch (WebApplicationException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Keycloak session logout failed", ex);
            } catch (Exception ex) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Keycloak session logout failed", ex);
            }
        });
    }

    private <T> T runKeycloakCall(Supplier<T> action) {
        return circuitBreakerFactory.create("keycloakAdmin").run(
                action,
                throwable -> {
                    if (throwable instanceof RuntimeException runtime) {
                        throw runtime;
                    }
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Keycloak admin operation failed", throwable);
                }
        );
    }

    private void runKeycloakVoid(Runnable action) {
        runKeycloakCall(() -> {
            action.run();
            return null;
        });
    }

    private UserRepresentation createUser(
            org.keycloak.admin.client.resource.RealmResource realmResource,
            String normalizedEmail,
            String firstName,
            String lastName
    ) {
        UserRepresentation user = new UserRepresentation();
        user.setUsername(normalizedEmail);
        user.setEmail(normalizedEmail);
        user.setEnabled(true);
        user.setEmailVerified(false);
        if (StringUtils.hasText(firstName)) {
            user.setFirstName(firstName.trim());
        }
        if (StringUtils.hasText(lastName)) {
            user.setLastName(lastName.trim());
        }
        List<String> requiredActions = new ArrayList<>();
        requiredActions.add("VERIFY_EMAIL");
        requiredActions.add("UPDATE_PASSWORD");
        user.setRequiredActions(requiredActions);

        try (Response response = realmResource.users().create(user)) {
            int status = response.getStatus();
            if (status == Response.Status.CREATED.getStatusCode()) {
                String userId = extractUserIdFromLocation(response.getHeaderString("Location"));
                if (!StringUtils.hasText(userId)) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Keycloak created user without id");
                }
                return realmResource.users().get(userId).toRepresentation();
            }
            if (status == Response.Status.CONFLICT.getStatusCode()) {
                return findUserByEmail(realmResource.users().searchByEmail(normalizedEmail, true), normalizedEmail);
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Keycloak user creation failed (" + status + ")");
        }
    }

    private void updateNamesIfProvided(org.keycloak.admin.client.resource.RealmResource realmResource, UserRepresentation user, String firstName, String lastName) {
        boolean changed = false;
        if (StringUtils.hasText(firstName)) {
            String normalizedFirst = firstName.trim();
            if (!normalizedFirst.equals(user.getFirstName())) {
                user.setFirstName(normalizedFirst);
                changed = true;
            }
        }
        if (StringUtils.hasText(lastName)) {
            String normalizedLast = lastName.trim();
            if (!normalizedLast.equals(user.getLastName())) {
                user.setLastName(normalizedLast);
                changed = true;
            }
        }
        if (changed) {
            realmResource.users().get(user.getId()).update(user);
        }
    }

    private void assignRealmRoleIfMissing(org.keycloak.admin.client.resource.RealmResource realmResource, String userId, String roleName) {
        RoleRepresentation roleRepresentation;
        try {
            roleRepresentation = realmResource.roles().get(roleName).toRepresentation();
        } catch (NotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Keycloak realm role not found: " + roleName);
        }
        var realmLevel = realmResource.users().get(userId).roles().realmLevel();
        List<RoleRepresentation> effective = realmLevel.listEffective();
        boolean alreadyAssigned = effective != null && effective.stream()
                .anyMatch(role -> roleName.equalsIgnoreCase(role.getName()));
        if (!alreadyAssigned) {
            realmLevel.add(List.of(roleRepresentation));
        }
    }

    private void collectUsers(Map<String, UserRepresentation> unique, List<UserRepresentation> users, int limit) {
        if (users == null || users.isEmpty() || unique.size() >= limit) {
            return;
        }
        for (UserRepresentation user : users) {
            if (user == null || !StringUtils.hasText(user.getId())) {
                continue;
            }
            unique.putIfAbsent(user.getId(), user);
            if (unique.size() >= limit) {
                return;
            }
        }
    }

    private void sendRequiredActionsEmail(org.keycloak.admin.client.resource.RealmResource realmResource, String userId) {
        try {
            realmResource.users()
                    .get(userId)
                    .executeActionsEmail(List.of("VERIFY_EMAIL", "UPDATE_PASSWORD"));
        } catch (WebApplicationException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Keycloak action email failed. Check Keycloak SMTP/email settings.",
                    ex
            );
        }
    }

    private UserRepresentation findUserByEmail(List<UserRepresentation> users, String normalizedEmail) {
        if (users == null || users.isEmpty()) {
            return null;
        }
        return users.stream()
                .filter(user -> normalizedEmail.equalsIgnoreCase(resolveEmail(user)))
                .findFirst()
                .orElse(users.getFirst());
    }

    private String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email is required");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String resolveEmail(UserRepresentation user) {
        if (user == null) {
            return "";
        }
        if (StringUtils.hasText(user.getEmail())) {
            return user.getEmail().trim().toLowerCase(Locale.ROOT);
        }
        if (StringUtils.hasText(user.getUsername())) {
            return user.getUsername().trim().toLowerCase(Locale.ROOT);
        }
        return "";
    }

    private String normalizeOptional(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String composeDisplayName(UserRepresentation user) {
        String first = normalizeOptional(user == null ? null : user.getFirstName());
        String last = normalizeOptional(user == null ? null : user.getLastName());
        String full = ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
        if (!full.isEmpty()) {
            return full;
        }
        String email = resolveEmail(user);
        if (!email.isBlank()) {
            return email;
        }
        return normalizeOptional(user == null ? null : user.getUsername());
    }

    private volatile Keycloak sharedAdminClient;

    private Keycloak getOrCreateAdminClient() {
        Keycloak client = sharedAdminClient;
        if (client != null) {
            return client;
        }
        synchronized (this) {
            if (sharedAdminClient == null) {
                sharedAdminClient = KeycloakBuilder.builder()
                        .serverUrl(serverUrl)
                        .realm(adminRealm)
                        .clientId(clientId)
                        .clientSecret(clientSecret)
                        .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                        .build();
            }
            return sharedAdminClient;
        }
    }

    private String resolveBaseUrlFromIssuer(String issuerUri) {
        if (!StringUtils.hasText(issuerUri)) {
            throw new IllegalArgumentException("keycloak.issuer-uri is required");
        }
        String normalized = issuerUri.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        int realmsSegmentIndex = normalized.indexOf("/realms/");
        if (realmsSegmentIndex < 0) {
            throw new IllegalArgumentException("Issuer URI must contain '/realms/' segment: " + issuerUri);
        }
        return normalized.substring(0, realmsSegmentIndex);
    }

    private String extractUserIdFromLocation(String locationHeader) {
        if (!StringUtils.hasText(locationHeader)) {
            return null;
        }
        int separatorIndex = locationHeader.lastIndexOf('/');
        if (separatorIndex < 0 || separatorIndex == locationHeader.length() - 1) {
            return null;
        }
        String candidate = locationHeader.substring(separatorIndex + 1).trim();
        return candidate.isBlank() ? null : candidate;
    }

    public record KeycloakManagedUser(
            String id,
            String email,
            String firstName,
            String lastName,
            boolean created,
            boolean actionEmailSent
    ) {
    }

    public record KeycloakUserSearchResult(
            String id,
            String email,
            String username,
            String firstName,
            String lastName,
            String displayName,
            boolean enabled,
            boolean emailVerified
    ) {
    }
}
