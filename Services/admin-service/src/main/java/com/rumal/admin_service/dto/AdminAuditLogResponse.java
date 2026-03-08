package com.rumal.admin_service.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminAuditLogResponse(
        UUID id,
        String actorKeycloakId,
        String actorTenantId,
        String actorRoles,
        String actorType,
        String action,
        String resourceType,
        String resourceId,
        String changeSource,
        String details,
        String changeSet,
        String ipAddress,
        String userAgent,
        String requestId,
        Instant createdAt
) {
}
