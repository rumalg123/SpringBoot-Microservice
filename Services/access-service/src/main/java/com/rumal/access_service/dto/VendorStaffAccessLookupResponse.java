package com.rumal.access_service.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record VendorStaffAccessLookupResponse(
        UUID vendorId,
        String keycloakUserId,
        boolean active,
        Set<String> permissions,
        Instant accessExpiresAt,
        String allowedIps
) {
}
