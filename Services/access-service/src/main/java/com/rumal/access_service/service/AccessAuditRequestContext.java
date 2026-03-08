package com.rumal.access_service.service;

public record AccessAuditRequestContext(
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
