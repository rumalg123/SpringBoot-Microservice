package com.rumal.product_service.dto;

import java.time.Instant;
import java.util.UUID;

public record ProductMutationAuditResponse(
        UUID id,
        UUID productId,
        UUID vendorId,
        String action,
        String actorSub,
        String actorTenantId,
        String actorRoles,
        String actorType,
        String changeSource,
        String details,
        String changeSet,
        String clientIp,
        String userAgent,
        String requestId,
        Instant createdAt
) {
}
