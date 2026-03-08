package com.rumal.vendor_service.service;

public record VendorAuditRequestContext(
        String actorSub,
        String actorTenantId,
        String actorRoles,
        String actorType,
        String changeSource,
        String clientIp,
        String userAgent,
        String requestId
) {
}
