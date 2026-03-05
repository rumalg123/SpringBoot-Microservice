package com.rumal.analytics_service.client.dto;

import java.time.Instant;
import java.util.Set;

public record PlatformAccessLookup(
        String keycloakUserId,
        boolean active,
        Set<String> permissions,
        Instant accessExpiresAt,
        boolean mfaRequired,
        String allowedIps
) {
}
