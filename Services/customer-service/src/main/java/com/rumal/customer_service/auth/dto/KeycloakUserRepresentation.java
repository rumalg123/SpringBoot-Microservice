package com.rumal.customer_service.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakUserRepresentation(
        String id,
        String email,
        String username,
        String firstName,
        String lastName
) {
}
