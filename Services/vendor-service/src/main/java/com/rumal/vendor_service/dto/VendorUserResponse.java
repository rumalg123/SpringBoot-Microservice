package com.rumal.vendor_service.dto;

import com.rumal.vendor_service.entity.VendorUserRole;

import java.time.Instant;
import java.util.UUID;

public record VendorUserResponse(
        UUID id,
        UUID vendorId,
        String keycloakUserId,
        String email,
        String displayName,
        VendorUserRole role,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
