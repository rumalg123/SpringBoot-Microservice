package com.rumal.analytics_service.client.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record VendorStaffAccessLookup(
        UUID vendorId,
        String keycloakUserId,
        boolean active,
        Set<String> permissions,
        boolean mfaRequired,
        Instant accessExpiresAt,
        String allowedIps
) {
}
