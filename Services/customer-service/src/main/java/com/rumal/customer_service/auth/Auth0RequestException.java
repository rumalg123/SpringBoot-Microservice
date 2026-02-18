package com.rumal.customer_service.auth;

public class Auth0RequestException extends RuntimeException {
    public Auth0RequestException(String message) {
        super(message);
    }

    public Auth0RequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
