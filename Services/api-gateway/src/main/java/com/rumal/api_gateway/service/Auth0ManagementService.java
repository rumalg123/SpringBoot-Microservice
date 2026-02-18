package com.rumal.api_gateway.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Service
public class Auth0ManagementService {

    private final WebClient webClient;
    private final String clientId;
    private final String clientSecret;
    private final String audience;

    public Auth0ManagementService(
            WebClient.Builder webClientBuilder,
            @Value("${auth0.domain}") String domain,
            @Value("${auth0.mgmt.client-id}") String clientId,
            @Value("${auth0.mgmt.client-secret}") String clientSecret,
            @Value("${auth0.mgmt.audience}") String audience
    ) {
        this.webClient = webClientBuilder.baseUrl("https://" + domain).build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.audience = audience;
    }

    public Mono<Void> resendVerificationEmail(String userId) {
        if (userId == null || userId.isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing authenticated user"));
        }
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Auth0 management credentials are not configured"));
        }

        return getAccessToken()
                .flatMap(token -> webClient.post()
                        .uri("/api/v2/jobs/verification-email")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new VerificationEmailJobRequest(userId))
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, response ->
                                response.bodyToMono(String.class)
                                        .defaultIfEmpty("")
                                        .flatMap(body -> Mono.error(new ResponseStatusException(
                                                HttpStatus.BAD_GATEWAY,
                                                "Auth0 verification email request failed: " + body))))
                        .toBodilessEntity()
                        .then());
    }

    private Mono<String> getAccessToken() {
        return webClient.post()
                .uri("/oauth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ClientCredentialsRequest(clientId, clientSecret, audience))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new ResponseStatusException(
                                        HttpStatus.BAD_GATEWAY,
                                        "Auth0 token request failed: " + body))))
                .bodyToMono(Auth0AccessTokenResponse.class)
                .map(Auth0AccessTokenResponse::accessToken)
                .filter(token -> token != null && !token.isBlank())
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY, "Auth0 access token is empty")));
    }

    private record ClientCredentialsRequest(
            String client_id,
            String client_secret,
            String audience,
            String grant_type
    ) {
        private ClientCredentialsRequest(String clientId, String clientSecret, String audience) {
            this(clientId, clientSecret, audience, "client_credentials");
        }
    }

    private record Auth0AccessTokenResponse(String access_token) {
        private String accessToken() {
            return access_token;
        }
    }

    private record VerificationEmailJobRequest(String user_id) {
    }
}
