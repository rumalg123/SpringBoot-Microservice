package com.rumal.customer_service.auth.dto;

import java.util.List;

public record CreateKeycloakUserRequest(
        String username,
        String email,
        boolean enabled,
        boolean emailVerified,
        String firstName,
        List<Credential> credentials
) {
    public CreateKeycloakUserRequest(String email, String name, String password) {
        this(
                email,
                email,
                true,
                false,
                name,
                List.of(new Credential("password", password, false))
        );
    }

    public record Credential(
            String type,
            String value,
            boolean temporary
    ) {
    }
}
