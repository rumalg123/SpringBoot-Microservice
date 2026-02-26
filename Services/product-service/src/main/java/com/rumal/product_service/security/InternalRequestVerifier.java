package com.rumal.product_service.security;

import com.rumal.product_service.exception.UnauthorizedException;
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
        String payload = timestampStr + ":" + request.getMethod() + ":" + request.getRequestURI();
        String expected = computeHmac(payload);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
            throw new UnauthorizedException("Invalid internal HMAC signature");
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

    public static String sign(String secret, String method, String path) {
        long timestamp = System.currentTimeMillis();
        String payload = timestamp + ":" + method + ":" + path;
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return timestamp + ":" + HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return timestamp + ":";
        }
    }
}
