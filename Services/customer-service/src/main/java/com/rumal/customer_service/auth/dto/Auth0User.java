package com.rumal.customer_service.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Auth0User(
        @JsonProperty("user_id")
        String userId,
        String email,
        String name
) {}
