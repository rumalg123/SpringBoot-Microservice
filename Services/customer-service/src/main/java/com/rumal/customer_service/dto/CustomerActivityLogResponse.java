package com.rumal.customer_service.dto;

import java.time.Instant;
import java.util.UUID;

public record CustomerActivityLogResponse(
        UUID id,
        UUID customerId,
        String action,
        String details,
        String ipAddress,
        Instant createdAt
) {
}
