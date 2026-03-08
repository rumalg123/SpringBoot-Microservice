package com.rumal.order_service.dto;

import java.time.Instant;
import java.util.UUID;

public record OrderStatusAuditResponse(
        UUID id,
        String fromStatus,
        String toStatus,
        String actorSub,
        String actorTenantId,
        String actorRoles,
        String actorType,
        String changeSource,
        String note,
        String changeSet,
        String clientIp,
        String userAgent,
        String requestId,
        Instant createdAt
) {
}
