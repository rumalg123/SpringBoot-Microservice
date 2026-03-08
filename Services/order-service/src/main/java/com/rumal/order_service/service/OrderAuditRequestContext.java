package com.rumal.order_service.service;

public record OrderAuditRequestContext(
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
