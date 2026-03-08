package com.rumal.admin_service.service;

public record AdminAuditRequestContext(
        String actorKeycloakId,
        String actorTenantId,
        String actorRoles,
        String actorType,
        String changeSource,
        String ipAddress,
        String userAgent,
        String requestId
) {
}
