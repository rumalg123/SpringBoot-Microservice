package com.rumal.order_service.dto;

import java.time.Instant;
import java.util.UUID;

public record VendorOrderStatusAuditResponse(
        UUID id,
        String fromStatus,
        String toStatus,
        String actorSub,
        String actorRoles,
        String actorType,
        String changeSource,
        String note,
        Instant createdAt
) {
}
