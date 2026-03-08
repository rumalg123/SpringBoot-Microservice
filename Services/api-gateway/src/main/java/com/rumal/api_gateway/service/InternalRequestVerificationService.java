package com.rumal.api_gateway.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class InternalRequestVerificationService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final byte[] sharedSecretBytes;
    private final long maxTimestampDriftMs;

    public InternalRequestVerificationService(
            @Value("${internal.auth.shared-secret:}") String sharedSecret,
            @Value("${internal.auth.max-timestamp-drift-ms:60000}") long maxTimestampDriftMs
    ) {
        this.sharedSecretBytes = (sharedSecret == null ? "" : sharedSecret.trim()).getBytes(StandardCharsets.UTF_8);
        this.maxTimestampDriftMs = maxTimestampDriftMs > 0 ? maxTimestampDriftMs : 60_000L;
    }

    public void verify(ServerHttpRequest request) {
        if (sharedSecretBytes.length == 0) {
            throw new ResponseStatusException(UNAUTHORIZED, "Internal auth secret is not configured");
        }

        String providedSecret = header(request, "X-Internal-Auth");
        if (!StringUtils.hasText(providedSecret)
                || !MessageDigest.isEqual(sharedSecretBytes, providedSecret.trim().getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid internal authentication header");
        }

        String timestampValue = header(request, "X-Internal-Timestamp");
        String signature = header(request, "X-Internal-Signature");
        if (!StringUtils.hasText(timestampValue) || !StringUtils.hasText(signature)) {
            throw new ResponseStatusException(UNAUTHORIZED, "Internal HMAC headers are required");
        }

        long timestamp = parseTimestamp(timestampValue);
        if (Math.abs(System.currentTimeMillis() - timestamp) > maxTimestampDriftMs) {
            throw new ResponseStatusException(UNAUTHORIZED, "Internal request timestamp expired");
        }

        String method = request.getMethod() == null ? HttpMethod.GET.name() : request.getMethod().name();
        String actualPath = request.getPath().value();
        String signedPath = header(request, "X-Internal-Path");
        if (StringUtils.hasText(signedPath) && !actualPath.equals(signedPath.trim())) {
            throw new ResponseStatusException(UNAUTHORIZED, "Internal request path mismatch");
        }

        String bodyHash = header(request, "X-Internal-Body-Hash");
        if (!requiresBodyHash(method) && StringUtils.hasText(bodyHash)) {
            throw new ResponseStatusException(UNAUTHORIZED, "Unexpected internal body hash header");
        }
        String payload = timestampValue.trim() + ":" + method + ":" + actualPath + ":" + (bodyHash == null ? "" : bodyHash.trim());
        String expectedSignature = computeHmac(payload);
        if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8), signature.trim().getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid internal HMAC signature");
        }
    }

    private boolean requiresBodyHash(String method) {
        return HttpMethod.POST.name().equals(method)
                || HttpMethod.PUT.name().equals(method)
                || HttpMethod.PATCH.name().equals(method);
    }

    private String header(ServerHttpRequest request, String name) {
        String value = request.getHeaders().getFirst(name);
        return value == null ? "" : value;
    }

    private long parseTimestamp(String rawValue) {
        try {
            return Long.parseLong(rawValue.trim());
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid internal timestamp header");
        }
    }

    private String computeHmac(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(sharedSecretBytes, HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new ResponseStatusException(UNAUTHORIZED, "Unable to validate internal HMAC signature");
        }
    }
}
