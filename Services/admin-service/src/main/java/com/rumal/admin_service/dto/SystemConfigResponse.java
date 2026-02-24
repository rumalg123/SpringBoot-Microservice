package com.rumal.admin_service.dto;

import java.time.Instant;
import java.util.UUID;

public record SystemConfigResponse(
        UUID id,
        String configKey,
        String configValue,
        String description,
        String valueType,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
