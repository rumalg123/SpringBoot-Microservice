package com.rumal.personalization_service.exception;

import org.springframework.http.HttpStatusCode;

public class DownstreamHttpException extends RuntimeException {

    private final HttpStatusCode statusCode;

    public DownstreamHttpException(HttpStatusCode statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public HttpStatusCode getStatusCode() {
        return statusCode;
    }
}
