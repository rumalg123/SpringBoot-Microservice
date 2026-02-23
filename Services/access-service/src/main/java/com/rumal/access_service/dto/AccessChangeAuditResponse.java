package com.rumal.access_service.dto;

import com.rumal.access_service.entity.AccessChangeAction;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AccessChangeAuditResponse(
        UUID id,
        String targetType,
        UUID targetId,
        UUID vendorId,
        String keycloakUserId,
        String email,
        AccessChangeAction action,
        boolean activeAfter,
        boolean deletedAfter,
        List<String> permissions,
        String actorSub,
        String actorRoles,
        String actorType,
        String changeSource,
        String reason,
        Instant createdAt
) {
}
