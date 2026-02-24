package com.rumal.admin_service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpsertFeatureFlagRequest(
        @NotBlank @Size(max = 200) String flagKey,
        @Size(max = 500) String description,
        Boolean enabled,
        @Size(max = 500) String enabledForRoles,
        @Min(0) @Max(100) Integer rolloutPercentage
) {
}
