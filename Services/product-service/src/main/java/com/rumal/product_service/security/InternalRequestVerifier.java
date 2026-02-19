package com.rumal.product_service.security;

import com.rumal.product_service.exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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
        if (headerValue == null || !sharedSecret.equals(headerValue)) {
            throw new UnauthorizedException("Invalid internal authentication header");
        }
    }
}
