package com.rumal.vendor_service.dto;

import com.rumal.vendor_service.entity.VendorLifecycleAction;

import java.time.Instant;
import java.util.UUID;

public record VendorLifecycleAuditResponse(
        UUID id,
        UUID vendorId,
        VendorLifecycleAction action,
        String resourceType,
        String resourceId,
        String actorSub,
        String actorTenantId,
        String actorRoles,
        String actorType,
        String changeSource,
        String reason,
        String changeSet,
        String clientIp,
        String userAgent,
        String requestId,
        Instant createdAt
) {
}
