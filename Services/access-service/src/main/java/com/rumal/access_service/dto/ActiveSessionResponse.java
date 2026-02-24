package com.rumal.access_service.dto;

import java.time.Instant;
import java.util.UUID;

public record ActiveSessionResponse(
        UUID id,
        String keycloakId,
        String ipAddress,
        String userAgent,
        Instant lastActivityAt,
        Instant createdAt
) {
}
