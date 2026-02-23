package com.rumal.product_service.dto;

import java.util.Set;
import java.util.UUID;

public record VendorStaffAccessLookupResponse(
        UUID vendorId,
        String keycloakUserId,
        boolean active,
        Set<String> permissions
) {
}
