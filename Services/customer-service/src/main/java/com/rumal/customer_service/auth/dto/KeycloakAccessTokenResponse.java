package com.rumal.customer_service.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KeycloakAccessTokenResponse(
        @JsonProperty("access_token")
        String accessToken
) {
}
