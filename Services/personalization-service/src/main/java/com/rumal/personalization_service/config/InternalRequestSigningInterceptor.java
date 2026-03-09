package com.rumal.personalization_service.config;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class InternalRequestSigningInterceptor implements ClientHttpRequestInterceptor {

    private static final String HMAC_ALGO = "HmacSHA256";

    private final String internalAuthSharedSecret;

    public InternalRequestSigningInterceptor(String internalAuthSharedSecret) {
        this.internalAuthSharedSecret = internalAuthSharedSecret == null ? "" : internalAuthSharedSecret.trim();
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        applyInternalHmacHeaders(request, body);
        return execution.execute(request, body);
    }

    private void applyInternalHmacHeaders(HttpRequest request, byte[] body) {
        if (internalAuthSharedSecret.isEmpty()) {
            return;
        }

        String internalHeader = request.getHeaders().getFirst("X-Internal-Auth");
        if (internalHeader == null || internalHeader.isBlank()) {
            return;
        }

        String timestamp = String.valueOf(System.currentTimeMillis());
        String method = request.getMethod() == null ? "GET" : request.getMethod().name();
        String path = request.getURI().getRawPath();
        String bodyHash = computeBodyHash(method, body);
        String payload = timestamp + ":" + method + ":" + path + ":" + bodyHash;
        String signature = computeHmac(payload);

        request.getHeaders().set("X-Internal-Timestamp", timestamp);
        request.getHeaders().set("X-Internal-Signature", signature);
        request.getHeaders().set("X-Internal-Path", path);
        request.getHeaders().set("X-Internal-Body-Hash", bodyHash);
    }

    private String computeBodyHash(String method, byte[] body) {
        if (!"POST".equals(method) && !"PUT".equals(method) && !"PATCH".equals(method)) {
            return "";
        }
        if (body == null || body.length == 0) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(body));
        } catch (Exception ex) {
            return "";
        }
    }

    private String computeHmac(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(internalAuthSharedSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            return "";
        }
    }
}
