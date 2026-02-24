package com.rumal.access_service.dto;

import com.rumal.access_service.entity.PermissionGroupScope;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PermissionGroupResponse(
        UUID id,
        String name,
        String description,
        List<String> permissions,
        PermissionGroupScope scope,
        Instant createdAt
) {
}
