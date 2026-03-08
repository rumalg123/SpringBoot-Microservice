package com.rumal.access_service.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

@Component
public class GatewaySessionClient {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final RestClient restClient;
    private final String internalSharedSecret;

    public GatewaySessionClient(
            @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder loadBalancedRestClientBuilder,
            @Value("${internal.auth.shared-secret:}") String internalSharedSecret
    ) {
        this.restClient = loadBalancedRestClientBuilder
                .baseUrl("http://api-gateway")
                .build();
        this.internalSharedSecret = internalSharedSecret == null ? "" : internalSharedSecret.trim();
    }

    public void revokeSessionByKeycloakSessionId(String keycloakSessionId) {
        if (!StringUtils.hasText(keycloakSessionId)) {
            return;
        }
        requireInternalSecret();
        String encoded = UriUtils.encodePathSegment(keycloakSessionId.trim(), StandardCharsets.UTF_8);
        String path = "/internal/auth/sessions/by-keycloak-session/" + encoded;
        delete(path, "Unable to revoke gateway session handle");
    }

    public void revokeAllSessionsForKeycloakUser(String keycloakUserId) {
        if (!StringUtils.hasText(keycloakUserId)) {
            return;
        }
        requireInternalSecret();
        String encoded = UriUtils.encodePathSegment(keycloakUserId.trim(), StandardCharsets.UTF_8);
        String path = "/internal/auth/sessions/by-keycloak-user/" + encoded;
        delete(path, "Unable to revoke gateway subject sessions");
    }

    private void delete(String path, String failureMessage) {
        byte[] body = new byte[0];
        restClient.delete()
                .uri(path)
                .headers(headers -> applyInternalHeaders(headers, HttpMethod.DELETE, path, body))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, failureMessage);
                })
                .toBodilessEntity();
    }

    private void requireInternalSecret() {
        if (!StringUtils.hasText(internalSharedSecret)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Internal auth shared secret is not configured");
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

    private String computeHmac(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(internalSharedSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to sign gateway revocation request", ex);
        }
    }

    private String sha256Hex(byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value));
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to hash gateway revocation request body", ex);
        }
    }
}
