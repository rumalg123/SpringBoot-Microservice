package com.rumal.wishlist_service.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        Map<String, String> fieldErrors
) {
    public ApiError(Instant timestamp, int status, String error, String message) {
        this(timestamp, status, error, message, null);
    }
}
