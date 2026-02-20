package com.rumal.api_gateway.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class KeycloakManagementService {

    private final WebClient webClient;
    private final String realm;
    private final String adminRealm;
    private final String adminClientId;
    private final String adminClientSecret;

    public KeycloakManagementService(
            WebClient.Builder webClientBuilder,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${keycloak.realm}") String realm,
            @Value("${KEYCLOAK_ADMIN_REALM:${keycloak.realm}}") String adminRealm,
            @Value("${keycloak.admin.client-id}") String adminClientId,
            @Value("${keycloak.admin.client-secret}") String adminClientSecret
    ) {
        this.webClient = webClientBuilder.baseUrl(resolveBaseUrlFromIssuer(issuerUri)).build();
        this.realm = realm;
        this.adminRealm = adminRealm;
        this.adminClientId = adminClientId;
        this.adminClientSecret = adminClientSecret;
    }

    public Mono<Void> resendVerificationEmail(String userId) {
        if (!StringUtils.hasText(userId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing authenticated user"));
        }
        if (!StringUtils.hasText(adminClientId) || !StringUtils.hasText(adminClientSecret)) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Keycloak admin credentials are not configured"
            ));
        }

        return getAccessToken()
                .flatMap(token -> webClient.put()
                        .uri(uriBuilder -> uriBuilder
                                .path("/admin/realms/{realm}/users/{userId}/execute-actions-email")
                                .build(realm, userId))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(List.of("VERIFY_EMAIL"))
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, response ->
                                response.bodyToMono(String.class)
                                        .defaultIfEmpty("")
                                        .flatMap(body -> Mono.error(new ResponseStatusException(
                                                HttpStatus.BAD_GATEWAY,
                                                "Keycloak verification email request failed: " + body))))
                        .toBodilessEntity()
                        .then());
    }

    private Mono<String> getAccessToken() {
        return webClient.post()
                .uri("/realms/{realm}/protocol/openid-connect/token", adminRealm)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("client_id", adminClientId)
                        .with("client_secret", adminClientSecret)
                        .with("grant_type", "client_credentials"))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new ResponseStatusException(
                                        HttpStatus.BAD_GATEWAY,
                                        "Keycloak token request failed: " + body))))
                .bodyToMono(KeycloakAccessTokenResponse.class)
                .map(KeycloakAccessTokenResponse::accessToken)
                .filter(StringUtils::hasText)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Keycloak access token is empty"
                )));
    }

    private String resolveBaseUrlFromIssuer(String issuerUri) {
        if (!StringUtils.hasText(issuerUri)) {
            throw new IllegalArgumentException("spring.security.oauth2.resourceserver.jwt.issuer-uri is required");
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

    private record KeycloakAccessTokenResponse(String access_token) {
        private String accessToken() {
            return access_token;
        }
    }
}
