package com.rumal.customer_service.auth;

public class KeycloakUserExistsException extends RuntimeException {
    public KeycloakUserExistsException(String message) {
        super(message);
    }
}
