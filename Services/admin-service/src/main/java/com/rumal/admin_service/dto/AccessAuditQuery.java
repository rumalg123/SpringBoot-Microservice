package com.rumal.admin_service.dto;

import java.util.UUID;

public record AccessAuditQuery(
        String targetType,
        UUID targetId,
        UUID vendorId,
        String action,
        String actorQuery,
        String from,
        String to,
        Integer page,
        Integer size,
        Integer limit
) {
}
