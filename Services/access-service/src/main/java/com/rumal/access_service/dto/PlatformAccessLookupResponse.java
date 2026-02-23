package com.rumal.access_service.dto;

import java.util.Set;

public record PlatformAccessLookupResponse(
        String keycloakUserId,
        boolean active,
        Set<String> permissions
) {
}
