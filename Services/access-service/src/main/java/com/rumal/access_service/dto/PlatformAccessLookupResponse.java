package com.rumal.access_service.dto;

import java.time.Instant;
import java.util.Set;

public record PlatformAccessLookupResponse(
        String keycloakUserId,
        boolean active,
        Set<String> permissions,
        Instant accessExpiresAt,
        boolean mfaRequired,
        String allowedIps
) {
}
