package com.rumal.customer_service.auth;

import com.rumal.customer_service.auth.dto.CreateKeycloakUserRequest;
import com.rumal.customer_service.auth.dto.KeycloakAccessTokenResponse;
import com.rumal.customer_service.auth.dto.KeycloakUser;
import com.rumal.customer_service.auth.dto.KeycloakUserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;

@Service
public class KeycloakManagementService {

    private final WebClient webClient;
    private final String realm;
    private final String adminRealm;
    private final String clientId;
    private final String clientSecret;

    public KeycloakManagementService(
            WebClient.Builder webClientBuilder,
            @Value("${keycloak.issuer-uri}") String issuerUri,
            @Value("${keycloak.realm}") String realm,
            @Value("${KEYCLOAK_ADMIN_REALM:${keycloak.realm}}") String adminRealm,
            @Value("${keycloak.admin.client-id}") String clientId,
            @Value("${keycloak.admin.client-secret}") String clientSecret
    ) {
        this.webClient = webClientBuilder.baseUrl(resolveBaseUrlFromIssuer(issuerUri)).build();
        this.realm = realm;
        this.adminRealm = adminRealm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public String createUser(String email, String password, String name) {
        String token = getAccessToken();
        try {
            ResponseEntity<Void> response = webClient.post()
                    .uri("/admin/realms/{realm}/users", realm)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new CreateKeycloakUserRequest(email, name, password))
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            if (response == null) {
                throw new KeycloakRequestException("Keycloak returned an empty create-user response");
            }

            String userId = extractUserIdFromLocation(response.getHeaders().getFirst(HttpHeaders.LOCATION));
            if (StringUtils.hasText(userId)) {
                return userId;
            }
            return getUserIdByEmail(email);
        } catch (WebClientResponseException ex) {
            if (ex.getStatusCode() == HttpStatus.CONFLICT) {
                throw new KeycloakUserExistsException("Keycloak user already exists for email: " + email);
            }
            throw new KeycloakRequestException("Keycloak user creation failed: " + ex.getMessage(), ex);
        }
    }

    public String getUserIdByEmail(String email) {
        String token = getAccessToken();
        String normalizedEmail = email.trim().toLowerCase();
        try {
            List<KeycloakUserRepresentation> users = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/admin/realms/{realm}/users")
                            .queryParam("email", normalizedEmail)
                            .queryParam("exact", true)
                            .build(realm))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<KeycloakUserRepresentation>>() {
                    })
                    .block();

            if (users == null || users.isEmpty()) {
                throw new KeycloakRequestException("Keycloak user not found for email: " + normalizedEmail);
            }

            KeycloakUserRepresentation match = users.stream()
                    .filter(user -> normalizedEmail.equalsIgnoreCase(resolveEmail(user)))
                    .findFirst()
                    .orElse(users.getFirst());

            if (match == null || !StringUtils.hasText(match.id())) {
                throw new KeycloakRequestException("Keycloak user id is missing for email: " + normalizedEmail);
            }

            return match.id();
        } catch (WebClientResponseException ex) {
            throw new KeycloakRequestException("Keycloak user lookup failed: " + ex.getMessage(), ex);
        }
    }

    public KeycloakUser getUserById(String userId) {
        String token = getAccessToken();
        try {
            KeycloakUserRepresentation user = webClient.get()
                    .uri("/admin/realms/{realm}/users/{id}", realm, userId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .bodyToMono(KeycloakUserRepresentation.class)
                    .block();

            if (user == null || !StringUtils.hasText(user.id())) {
                throw new KeycloakRequestException("Keycloak user not found for id: " + userId);
            }

            return new KeycloakUser(user.id(), resolveEmail(user), resolveDisplayName(user));
        } catch (WebClientResponseException ex) {
            throw new KeycloakRequestException("Keycloak user lookup failed: " + ex.getMessage(), ex);
        }
    }

    private String getAccessToken() {
        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(clientSecret)) {
            throw new KeycloakRequestException("Keycloak admin client credentials are not configured");
        }

        try {
            KeycloakAccessTokenResponse token = webClient.post()
                    .uri("/realms/{realm}/protocol/openid-connect/token", adminRealm)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData("client_id", clientId)
                            .with("client_secret", clientSecret)
                            .with("grant_type", "client_credentials"))
                    .retrieve()
                    .bodyToMono(KeycloakAccessTokenResponse.class)
                    .block();

            if (token == null || !StringUtils.hasText(token.accessToken())) {
                throw new KeycloakRequestException("Keycloak access token is empty");
            }
            return token.accessToken();
        } catch (WebClientResponseException ex) {
            throw new KeycloakRequestException("Keycloak token request failed: " + ex.getMessage(), ex);
        }
    }

    private String resolveEmail(KeycloakUserRepresentation user) {
        if (user == null) {
            return "";
        }
        if (StringUtils.hasText(user.email())) {
            return user.email().trim().toLowerCase();
        }
        if (StringUtils.hasText(user.username())) {
            return user.username().trim().toLowerCase();
        }
        return "";
    }

    private String resolveDisplayName(KeycloakUserRepresentation user) {
        if (user == null) {
            return "";
        }
        String firstName = StringUtils.hasText(user.firstName()) ? user.firstName().trim() : "";
        String lastName = StringUtils.hasText(user.lastName()) ? user.lastName().trim() : "";
        String fullName = (firstName + " " + lastName).trim();
        if (!fullName.isBlank()) {
            return fullName;
        }
        if (StringUtils.hasText(user.username())) {
            return user.username().trim();
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
}
