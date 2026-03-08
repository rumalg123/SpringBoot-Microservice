package com.rumal.product_service.service;

public record ProductAuditRequestContext(
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
