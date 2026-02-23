package com.rumal.access_service.dto;

import com.rumal.access_service.entity.PlatformPermission;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record PlatformStaffAccessResponse(
        UUID id,
        String keycloakUserId,
        String email,
        String displayName,
        Set<PlatformPermission> permissions,
        boolean active,
        boolean deleted,
        Instant deletedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
