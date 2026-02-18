package com.rumal.customer_service.auth.dto;

public record CreateAuth0UserRequest(
        String connection,
        String email,
        String password,
        String name
) {}
