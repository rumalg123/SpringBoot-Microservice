package com.rumal.admin_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpsertFeatureFlagRequest(
        @NotBlank @Size(max = 200) String flagKey,
        @Size(max = 500) String description,
        Boolean enabled,
        @Size(max = 500) String enabledForRoles,
        Integer rolloutPercentage
) {
}
