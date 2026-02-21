package com.rumal.customer_service.auth;

import com.rumal.customer_service.auth.dto.KeycloakUser;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class KeycloakManagementService {

    private final String serverUrl;
    private final String realm;
    private final String adminRealm;
    private final String clientId;
    private final String clientSecret;

    public KeycloakManagementService(
            @Value("${keycloak.issuer-uri}") String issuerUri,
            @Value("${keycloak.realm}") String realm,
            @Value("${KEYCLOAK_ADMIN_REALM:${keycloak.realm}}") String adminRealm,
            @Value("${keycloak.admin.client-id}") String clientId,
            @Value("${keycloak.admin.client-secret}") String clientSecret
    ) {
        this.serverUrl = resolveBaseUrlFromIssuer(issuerUri);
        this.realm = realm;
        this.adminRealm = adminRealm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        if (!StringUtils.hasText(this.clientId) || !StringUtils.hasText(this.clientSecret)) {
            throw new IllegalStateException("KEYCLOAK_ADMIN_CLIENT_ID and KEYCLOAK_ADMIN_CLIENT_SECRET must be configured");
        }
    }

    public String createUser(String email, String password, String name) {
        String normalizedEmail = email.trim().toLowerCase();
        try (Keycloak keycloak = newAdminClient()) {
            UserRepresentation user = new UserRepresentation();
            user.setUsername(normalizedEmail);
            user.setEmail(normalizedEmail);
            user.setEnabled(true);
            user.setEmailVerified(false);
            if (StringUtils.hasText(name)) {
                user.setFirstName(name.trim());
            }

            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(password);
            credential.setTemporary(false);
            user.setCredentials(List.of(credential));

            try (Response response = keycloak.realm(realm).users().create(user)) {
                int status = response.getStatus();
                if (status == Response.Status.CREATED.getStatusCode()) {
                    String userId = extractUserIdFromLocation(response.getHeaderString("Location"));
                    if (StringUtils.hasText(userId)) {
                        return userId;
                    }
                    return getUserIdByEmail(normalizedEmail);
                }
                if (status == Response.Status.CONFLICT.getStatusCode()) {
                    throw new KeycloakUserExistsException("Keycloak user already exists for email: " + normalizedEmail);
                }
                throw new KeycloakRequestException("Keycloak user creation failed (" + status + "): " + readResponseBody(response));
            }
        } catch (WebApplicationException ex) {
            Response response = ex.getResponse();
            if (response != null && response.getStatus() == Response.Status.CONFLICT.getStatusCode()) {
                throw new KeycloakUserExistsException("Keycloak user already exists for email: " + normalizedEmail);
            }
            throw new KeycloakRequestException("Keycloak user creation failed: " + ex.getMessage(), ex);
        }
    }

    public String getUserIdByEmail(String email) {
        String normalizedEmail = email.trim().toLowerCase();
        try (Keycloak keycloak = newAdminClient()) {
            UsersResource usersResource = keycloak.realm(realm).users();
            List<UserRepresentation> users = usersResource.searchByEmail(normalizedEmail, true);
            if (users == null || users.isEmpty()) {
                throw new KeycloakRequestException("Keycloak user not found for email: " + normalizedEmail);
            }

            UserRepresentation match = users.stream()
                    .filter(user -> normalizedEmail.equalsIgnoreCase(resolveEmail(user)))
                    .findFirst()
                    .orElse(users.getFirst());

            if (match == null || !StringUtils.hasText(match.getId())) {
                throw new KeycloakRequestException("Keycloak user id is missing for email: " + normalizedEmail);
            }
            return match.getId();
        } catch (WebApplicationException ex) {
            throw new KeycloakRequestException("Keycloak user lookup failed: " + ex.getMessage(), ex);
        }
    }

    public KeycloakUser getUserById(String userId) {
        try (Keycloak keycloak = newAdminClient()) {
            UserRepresentation user = keycloak.realm(realm).users().get(userId).toRepresentation();
            if (user == null || !StringUtils.hasText(user.getId())) {
                throw new KeycloakRequestException("Keycloak user not found for id: " + userId);
            }
            return new KeycloakUser(user.getId(), resolveEmail(user), resolveDisplayName(user));
        } catch (NotFoundException ex) {
            throw new KeycloakRequestException("Keycloak user not found for id: " + userId, ex);
        } catch (WebApplicationException ex) {
            throw new KeycloakRequestException("Keycloak user lookup failed: " + ex.getMessage(), ex);
        }
    }

    public void updateUserName(String userId, String fullName) {
        if (!StringUtils.hasText(userId)) {
            throw new KeycloakRequestException("Keycloak user id is required");
        }
        if (!StringUtils.hasText(fullName)) {
            throw new KeycloakRequestException("Keycloak full name is required");
        }

        String normalizedName = fullName.trim();
        NameParts parts = splitName(normalizedName);
        updateUserNames(userId, parts.firstName(), parts.lastName());
    }

    public void updateUserNames(String userId, String firstName, String lastName) {
        if (!StringUtils.hasText(userId)) {
            throw new KeycloakRequestException("Keycloak user id is required");
        }
        if (!StringUtils.hasText(firstName)) {
            throw new KeycloakRequestException("Keycloak first name is required");
        }
        if (!StringUtils.hasText(lastName)) {
            throw new KeycloakRequestException("Keycloak last name is required");
        }

        String normalizedFirstName = firstName.trim();
        String normalizedLastName = lastName.trim();

        try (Keycloak keycloak = newAdminClient()) {
            var userResource = keycloak.realm(realm).users().get(userId);
            UserRepresentation user = userResource.toRepresentation();
            if (user == null || !StringUtils.hasText(user.getId())) {
                throw new KeycloakRequestException("Keycloak user not found for id: " + userId);
            }
            user.setFirstName(normalizedFirstName);
            user.setLastName(normalizedLastName);
            userResource.update(user);
        } catch (NotFoundException ex) {
            throw new KeycloakRequestException("Keycloak user not found for id: " + userId, ex);
        } catch (WebApplicationException ex) {
            throw new KeycloakRequestException("Keycloak user update failed: " + ex.getMessage(), ex);
        }
    }

    private Keycloak newAdminClient() {
        return KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm(adminRealm)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .build();
    }

    private String readResponseBody(Response response) {
        if (response == null || !response.hasEntity()) {
            return "no response body";
        }
        try {
            String body = response.readEntity(String.class);
            return StringUtils.hasText(body) ? body : "no response body";
        } catch (Exception ignored) {
            return "unable to parse response body";
        }
    }

    private String resolveEmail(UserRepresentation user) {
        if (user == null) {
            return "";
        }
        if (StringUtils.hasText(user.getEmail())) {
            return user.getEmail().trim().toLowerCase();
        }
        if (StringUtils.hasText(user.getUsername())) {
            return user.getUsername().trim().toLowerCase();
        }
        return "";
    }

    private String resolveDisplayName(UserRepresentation user) {
        if (user == null) {
            return "";
        }
        String firstName = StringUtils.hasText(user.getFirstName()) ? user.getFirstName().trim() : "";
        String lastName = StringUtils.hasText(user.getLastName()) ? user.getLastName().trim() : "";
        String fullName = (firstName + " " + lastName).trim();
        if (!fullName.isBlank()) {
            return fullName;
        }
        if (StringUtils.hasText(user.getUsername())) {
            return user.getUsername().trim();
        }
        return resolveEmail(user);
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

    private NameParts splitName(String fullName) {
        String normalized = fullName.trim().replaceAll("\\s+", " ");
        int firstSpace = normalized.indexOf(' ');
        if (firstSpace < 0) {
            return new NameParts(normalized, "");
        }
        String first = normalized.substring(0, firstSpace).trim();
        String last = normalized.substring(firstSpace + 1).trim();
        return new NameParts(first, last);
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

    private record NameParts(String firstName, String lastName) {
    }
}
