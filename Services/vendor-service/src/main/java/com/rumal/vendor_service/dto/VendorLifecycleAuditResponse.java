package com.rumal.vendor_service.dto;

import com.rumal.vendor_service.entity.VendorLifecycleAction;

import java.time.Instant;
import java.util.UUID;

public record VendorLifecycleAuditResponse(
        UUID id,
        UUID vendorId,
        VendorLifecycleAction action,
        String actorSub,
        String actorRoles,
        String actorType,
        String changeSource,
        String reason,
        Instant createdAt
) {
}

