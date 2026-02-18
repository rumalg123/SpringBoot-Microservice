package com.rumal.customer_service.auth;

public class Auth0UserExistsException extends RuntimeException {
    public Auth0UserExistsException(String message) {
        super(message);
    }
}
