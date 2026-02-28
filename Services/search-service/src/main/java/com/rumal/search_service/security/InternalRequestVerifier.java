package com.rumal.search_service.security;

import com.rumal.search_service.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class InternalRequestVerifier {

    private static final String HMAC_ALGO = "HmacSHA256";
    private static final long MAX_TIMESTAMP_DRIFT_MS = 60_000;

    private final String sharedSecret;

    public InternalRequestVerifier(@Value("${internal.auth.shared-secret:}") String sharedSecret) {
        this.sharedSecret = sharedSecret;
    }

    public void verify(String headerValue) {
        if (sharedSecret == null || sharedSecret.isBlank()) {
            throw new UnauthorizedException("Internal auth secret is not configured");
        }
        if (headerValue == null || !MessageDigest.isEqual(sharedSecret.getBytes(StandardCharsets.UTF_8), headerValue.getBytes(StandardCharsets.UTF_8))) {
            throw new UnauthorizedException("Invalid internal authentication header");
        }
        verifyHmacFromRequest();
    }

    private void verifyHmacFromRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes sra)) {
            return;
        }
        HttpServletRequest request = sra.getRequest();
        String signature = request.getHeader("X-Internal-Signature");
        String timestampStr = request.getHeader("X-Internal-Timestamp");
        if (signature == null || timestampStr == null) {
            return; // HMAC not yet deployed by caller — graceful
        }
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampStr);
        } catch (NumberFormatException e) {
            throw new UnauthorizedException("Invalid internal timestamp header");
        }
        if (Math.abs(System.currentTimeMillis() - timestamp) > MAX_TIMESTAMP_DRIFT_MS) {
            throw new UnauthorizedException("Internal request timestamp expired");
        }
        // H-03: Include body hash in HMAC payload to prevent request body tampering
        String bodyHash = computeBodyHash(request);
        String payload = timestampStr + ":" + request.getMethod() + ":" + request.getRequestURI() + ":" + bodyHash;
        String expected = computeHmac(payload);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
            throw new UnauthorizedException("Invalid internal HMAC signature");
        }
    }

    private String computeBodyHash(HttpServletRequest request) {
        String method = request.getMethod();
        if (!"POST".equals(method) && !"PUT".equals(method) && !"PATCH".equals(method)) {
            return "";
        }
        try {
            byte[] body;
            if (request instanceof InternalRequestBodyCachingFilter.CachedBodyRequestWrapper cached) {
                body = cached.getCachedBody();
            } else {
                body = request.getInputStream().readAllBytes();
            }
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(body);
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return "";
        }
    }

    private String computeHmac(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(sharedSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new UnauthorizedException("Failed to compute HMAC signature");
        }
    }

    public static String sign(String secret, String method, String path, byte[] body) {
        long timestamp = System.currentTimeMillis();
        String bodyHash = "";
        if (body != null && body.length > 0
                && ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method))) {
            try {
                java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
                bodyHash = HexFormat.of().formatHex(digest.digest(body));
            } catch (Exception ignored) {}
        }
        String payload = timestamp + ":" + method + ":" + path + ":" + bodyHash;
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return timestamp + ":" + HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return timestamp + ":";
        }
    }

    // Keep backward-compatible overload
    public static String sign(String secret, String method, String path) {
        return sign(secret, method, path, null);
    }
}
