package com.rumal.analytics_service.client.dto;

import java.util.UUID;

public record VendorAccessMembershipLookup(
        UUID vendorId,
        String vendorSlug,
        String vendorName,
        String role
) {
}
