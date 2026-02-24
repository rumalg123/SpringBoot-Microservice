package com.rumal.access_service.dto;

import com.rumal.access_service.entity.PermissionGroupScope;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateApiKeyResponse(
        UUID id,
        String keycloakId,
        String name,
        PermissionGroupScope scope,
        List<String> permissions,
        boolean active,
        Instant expiresAt,
        Instant createdAt,
        String rawKey
) {
}
