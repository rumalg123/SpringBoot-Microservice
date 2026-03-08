package com.rumal.api_gateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rumal.api_gateway.config.TrustedProxyResolver;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

@Service
public class AccessSessionService {

    private static final int MAX_IP_LENGTH = 45;
    private static final int MAX_USER_AGENT_LENGTH = 500;
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final WebClient accessServiceClient;
    private final TrustedProxyResolver trustedProxyResolver;
    private final ObjectMapper objectMapper;
    private final String internalSharedSecret;

    public AccessSessionService(
            @Qualifier("loadBalancedWebClientBuilder") WebClient.Builder loadBalancedWebClientBuilder,
            TrustedProxyResolver trustedProxyResolver,
            ObjectMapper objectMapper,
            @Value("${internal.auth.shared-secret:}") String internalSharedSecret
    ) {
        this.accessServiceClient = loadBalancedWebClientBuilder
                .baseUrl("http://access-service")
                .build();
        this.trustedProxyResolver = trustedProxyResolver;
        this.objectMapper = objectMapper;
        this.internalSharedSecret = internalSharedSecret == null ? "" : internalSharedSecret.trim();
    }

    public Mono<Void> syncSession(String keycloakId, String keycloakSessionId, ServerWebExchange exchange) {
        if (!StringUtils.hasText(keycloakId) || !StringUtils.hasText(keycloakSessionId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Active session id is unavailable"));
        }
        requireInternalSecret();

        String path = "/internal/access/sessions";
        SessionRegistrationRequest request = new SessionRegistrationRequest(
                keycloakId.trim(),
                keycloakSessionId.trim(),
                normalize(trustedProxyResolver.resolveClientIp(exchange), MAX_IP_LENGTH),
                normalize(exchange.getRequest().getHeaders().getFirst(HttpHeaders.USER_AGENT), MAX_USER_AGENT_LENGTH)
        );
        byte[] body = serializeRequest(request);

        return accessServiceClient.post()
                .uri(path)
                .headers(headers -> applyInternalHeaders(headers, HttpMethod.POST, path, body))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(bodyText -> Mono.error(new ResponseStatusException(
                                HttpStatus.BAD_GATEWAY,
                                "Unable to persist active session"))))
                .toBodilessEntity()
                .then();
    }

    public Mono<Void> revokeSession(String keycloakSessionId) {
        if (!StringUtils.hasText(keycloakSessionId)) {
            return Mono.empty();
        }
        requireInternalSecret();
        String encodedSessionId = UriUtils.encodePathSegment(keycloakSessionId.trim(), StandardCharsets.UTF_8);
        String path = "/internal/access/sessions/by-keycloak-session/" + encodedSessionId;
        return delete(path, "Unable to revoke active session");
    }

    public Mono<Void> revokeAllSessions(String keycloakId) {
        if (!StringUtils.hasText(keycloakId)) {
            return Mono.empty();
        }
        requireInternalSecret();
        String encodedKeycloakId = UriUtils.encodePathSegment(keycloakId.trim(), StandardCharsets.UTF_8);
        String path = "/internal/access/sessions/by-keycloak-user/" + encodedKeycloakId;
        return delete(path, "Unable to revoke active sessions");
    }

    private Mono<Void> delete(String path, String failureMessage) {
        byte[] body = new byte[0];
        return accessServiceClient.delete()
                .uri(path)
                .headers(headers -> applyInternalHeaders(headers, HttpMethod.DELETE, path, body))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(bodyText -> Mono.error(new ResponseStatusException(
                                HttpStatus.BAD_GATEWAY,
                                failureMessage))))
                .toBodilessEntity()
                .then();
    }

    private void requireInternalSecret() {
        if (!StringUtils.hasText(internalSharedSecret)) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Internal auth shared secret is not configured"
            );
        }
    }

    private byte[] serializeRequest(SessionRegistrationRequest request) {
        try {
            return objectMapper.writeValueAsBytes(request);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to serialize active session payload",
                    e
            );
        }
    }

    private void applyInternalHeaders(HttpHeaders headers, HttpMethod method, String path, byte[] body) {
        String bodyHash = body.length == 0 ? "" : sha256Hex(body);
        String timestamp = String.valueOf(Instant.now().toEpochMilli());
        String payload = timestamp + ":" + method.name() + ":" + path + ":" + bodyHash;

        headers.set("X-Internal-Auth", internalSharedSecret);
        headers.set("X-Internal-Timestamp", timestamp);
        headers.set("X-Internal-Signature", computeHmac(payload));
        headers.set("X-Internal-Path", path);
        headers.set("X-Internal-Body-Hash", bodyHash);
    }

    private String normalize(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength);
    }

    private String computeHmac(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(internalSharedSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to sign internal access-service request",
                    e
            );
        }
    }

    private String sha256Hex(byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value));
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to hash internal access-service request body",
                    e
            );
        }
    }

    private record SessionRegistrationRequest(
            String keycloakId,
            String keycloakSessionId,
            String ipAddress,
            String userAgent
    ) {
    }
}
