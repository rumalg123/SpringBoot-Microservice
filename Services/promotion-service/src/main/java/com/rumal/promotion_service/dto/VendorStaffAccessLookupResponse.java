package com.rumal.promotion_service.dto;

import java.util.Set;
import java.util.UUID;

public record VendorStaffAccessLookupResponse(
        UUID vendorId,
        boolean active,
        Set<String> permissions
) {
}
