package com.rumal.access_service.dto;

import com.rumal.access_service.entity.VendorPermission;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record VendorStaffAccessResponse(
        UUID id,
        UUID vendorId,
        String keycloakUserId,
        String email,
        String displayName,
        Set<VendorPermission> permissions,
        UUID permissionGroupId,
        boolean mfaRequired,
        Instant accessExpiresAt,
        String allowedIps,
        boolean active,
        boolean deleted,
        Instant deletedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
