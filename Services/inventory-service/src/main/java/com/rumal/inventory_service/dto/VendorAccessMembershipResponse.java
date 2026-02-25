package com.rumal.inventory_service.dto;

import java.util.UUID;

public record VendorAccessMembershipResponse(
        UUID vendorId,
        String vendorSlug,
        String vendorName,
        String role
) {}
