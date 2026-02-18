package com.rumal.customer_service.auth;

import com.rumal.customer_service.auth.dto.Auth0AccessTokenResponse;
import com.rumal.customer_service.auth.dto.Auth0ErrorResponse;
import com.rumal.customer_service.auth.dto.Auth0User;
import com.rumal.customer_service.auth.dto.CreateAuth0UserRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;

@Service
public class Auth0ManagementService {

    private final WebClient webClient;
    private final String domain;
    private final String clientId;
    private final String clientSecret;
    private final String audience;
    private final String connection;

    public Auth0ManagementService(
            WebClient.Builder webClientBuilder,
            @Value("${auth0.domain}") String domain,
            @Value("${auth0.mgmt.client-id}") String clientId,
            @Value("${auth0.mgmt.client-secret}") String clientSecret,
            @Value("${auth0.mgmt.audience}") String audience,
            @Value("${auth0.connection}") String connection
    ) {
        this.webClient = webClientBuilder.baseUrl("https://" + domain).build();
        this.domain = domain;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.audience = audience;
        this.connection = connection;
    }

    public String createUser(String email, String password, String name) {
        String token = getAccessToken();
        try {
            Auth0User user = webClient.post()
                    .uri("/api/v2/users")
                    .header("Authorization", "Bearer " + token)
                    .bodyValue(new CreateAuth0UserRequest(connection, email, password, name))
                    .retrieve()
                    .bodyToMono(Auth0User.class)
                    .block();

            if (user == null || user.userId() == null || user.userId().isBlank()) {
                throw new Auth0RequestException("Auth0 returned an empty user_id");
            }
            return user.userId();
        } catch (WebClientResponseException ex) {
            if (ex.getStatusCode() == HttpStatus.CONFLICT) {
                Auth0ErrorResponse error = Auth0ErrorResponse.tryParse(ex.getResponseBodyAsString());
                if (error != null && "user_exists".equalsIgnoreCase(error.errorCode())) {
                    throw new Auth0UserExistsException("Auth0 user already exists for email: " + email);
                }
            }
            throw new Auth0RequestException("Auth0 user creation failed: " + ex.getMessage(), ex);
        }
    }

    public String getUserIdByEmail(String email) {
        String token = getAccessToken();
        List<Auth0User> users = webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v2/users-by-email")
                        .queryParam("email", email)
                        .build())
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<Auth0User>>() {})
                .block();

        if (users == null || users.isEmpty() || users.get(0).userId() == null) {
            throw new Auth0RequestException("Auth0 user not found for email: " + email);
        }
        return users.get(0).userId();
    }

    public Auth0User getUserById(String userId) {
        String token = getAccessToken();
        Auth0User user = webClient.get()
                .uri("/api/v2/users/{id}", userId)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(Auth0User.class)
                .block();

        if (user == null || user.userId() == null || user.userId().isBlank()) {
            throw new Auth0RequestException("Auth0 user not found for id: " + userId);
        }
        return user;
    }

    private String getAccessToken() {
        try {
            Auth0AccessTokenResponse token = webClient.post()
                    .uri("/oauth/token")
                    .bodyValue(new Auth0TokenRequest(clientId, clientSecret, audience))
                    .retrieve()
                    .bodyToMono(Auth0AccessTokenResponse.class)
                    .block();

            if (token == null || token.accessToken() == null || token.accessToken().isBlank()) {
                throw new Auth0RequestException("Auth0 access token is empty for domain: " + domain);
            }
            return token.accessToken();
        } catch (WebClientResponseException ex) {
            throw new Auth0RequestException("Auth0 token request failed: " + ex.getMessage(), ex);
        }
    }

    private record Auth0TokenRequest(
            String client_id,
            String client_secret,
            String audience,
            String grant_type
    ) {
        private Auth0TokenRequest(String clientId, String clientSecret, String audience) {
            this(clientId, clientSecret, audience, "client_credentials");
        }
    }
}
