package com.rumal.access_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterSessionRequest(
        @NotBlank @Size(max = 120) String keycloakId,
        @Size(max = 45) String ipAddress,
        @Size(max = 500) String userAgent
) {
}
