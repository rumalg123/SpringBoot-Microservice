package com.rumal.admin_service.dto;

import java.util.Map;
import java.util.UUID;

public record VendorAdminOnboardResponse(
        UUID vendorId,
        boolean keycloakUserCreated,
        String keycloakUserId,
        String email,
        String firstName,
        String lastName,
        Map<String, Object> vendorMembership
) {
}
