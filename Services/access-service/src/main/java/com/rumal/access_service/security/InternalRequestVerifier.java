package com.rumal.access_service.security;

import com.rumal.access_service.exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class InternalRequestVerifier {

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
    }
}
