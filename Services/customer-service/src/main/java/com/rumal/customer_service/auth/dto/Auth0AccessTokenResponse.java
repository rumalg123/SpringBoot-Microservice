package com.rumal.customer_service.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Auth0AccessTokenResponse(
        @JsonProperty("access_token")
        String accessToken,
        @JsonProperty("token_type")
        String tokenType,
        @JsonProperty("expires_in")
        Integer expiresIn
) {}
