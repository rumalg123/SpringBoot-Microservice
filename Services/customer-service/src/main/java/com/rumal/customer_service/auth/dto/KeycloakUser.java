package com.rumal.customer_service.auth.dto;

public record KeycloakUser(
        String userId,
        String email,
        String name
) {
}
