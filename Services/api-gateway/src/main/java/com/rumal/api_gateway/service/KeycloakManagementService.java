package com.rumal.api_gateway.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class KeycloakManagementService {

    private static final Logger log = LoggerFactory.getLogger(KeycloakManagementService.class);
    private static final Duration TOKEN_REFRESH_MARGIN = Duration.ofSeconds(30);

    private final WebClient webClient;
    private final String realm;
    private final String adminRealm;
    private final String adminClientId;
    private final String adminClientSecret;
    private final AtomicReference<CachedToken> cachedToken = new AtomicReference<>();

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
        if (!StringUtils.hasText(this.adminClientId) || !StringUtils.hasText(this.adminClientSecret)) {
            throw new IllegalStateException("KEYCLOAK_ADMIN_CLIENT_ID and KEYCLOAK_ADMIN_CLIENT_SECRET must be configured");
        }
    }

    public Mono<Void> resendVerificationEmail(String userId) {
        if (!StringUtils.hasText(userId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing authenticated user"));
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
                                        .doOnNext(body -> log.warn("Keycloak verification email failed: {}", body))
                                        .flatMap(body -> Mono.error(new ResponseStatusException(
                                                HttpStatus.BAD_GATEWAY,
                                                "Unable to send verification email. Please try again later."))))
                        .toBodilessEntity()
                        .then());
    }

    private Mono<String> getAccessToken() {
        CachedToken cached = cachedToken.get();
        if (cached != null && Instant.now().isBefore(cached.expiresAt())) {
            return Mono.just(cached.token());
        }
        return fetchAccessToken();
    }

    private Mono<String> fetchAccessToken() {
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
                                .doOnNext(body -> log.error("Keycloak token request failed: {}", body))
                                .flatMap(body -> Mono.error(new ResponseStatusException(
                                        HttpStatus.BAD_GATEWAY,
                                        "Identity provider unavailable"))))
                .bodyToMono(KeycloakAccessTokenResponse.class)
                .flatMap(resp -> {
                    String token = resp.accessToken();
                    if (!StringUtils.hasText(token)) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.BAD_GATEWAY, "Keycloak access token is empty"));
                    }
                    long ttl = resp.expiresIn() > 0 ? resp.expiresIn() : 300;
                    Instant expiresAt = Instant.now().plusSeconds(
                            Math.max(1, ttl - TOKEN_REFRESH_MARGIN.getSeconds()));
                    cachedToken.set(new CachedToken(token, expiresAt));
                    return Mono.just(token);
                });
    }

    public Mono<Void> revokeSession(String sessionId) {
        return getAccessToken()
                .flatMap(token -> webClient.delete()
                        .uri("/admin/realms/{realm}/sessions/{sessionId}", realm, sessionId)
                        .header("Authorization", "Bearer " + token)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, response -> Mono.empty())
                        .toBodilessEntity()
                        .then());
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

    private record CachedToken(String token, Instant expiresAt) {}

    private record KeycloakAccessTokenResponse(String access_token, long expires_in) {
        private String accessToken() {
            return access_token;
        }
        private long expiresIn() {
            return expires_in;
        }
    }
}
