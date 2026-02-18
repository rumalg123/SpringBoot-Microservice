package com.rumal.customer_service.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Auth0ErrorResponse(
        String statusCode,
        String error,
        String message,
        String errorCode
) {
    public static Auth0ErrorResponse tryParse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return new ObjectMapper().readValue(body, Auth0ErrorResponse.class);
        } catch (Exception ignored) {
            return null;
        }
    }
}
