package com.rumal.customer_service.auth;

public class KeycloakRequestException extends RuntimeException {
    public KeycloakRequestException(String message) {
        super(message);
    }

    public KeycloakRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
