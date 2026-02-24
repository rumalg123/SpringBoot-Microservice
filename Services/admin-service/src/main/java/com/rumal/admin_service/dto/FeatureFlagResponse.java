package com.rumal.admin_service.dto;

import java.time.Instant;
import java.util.UUID;

public record FeatureFlagResponse(
        UUID id,
        String flagKey,
        String description,
        boolean enabled,
        String enabledForRoles,
        Integer rolloutPercentage,
        Instant createdAt,
        Instant updatedAt
) {
}
