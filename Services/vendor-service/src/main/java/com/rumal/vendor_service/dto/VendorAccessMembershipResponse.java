package com.rumal.vendor_service.dto;

import com.rumal.vendor_service.entity.VendorUserRole;

import java.util.UUID;

public record VendorAccessMembershipResponse(
        UUID vendorId,
        String vendorSlug,
        String vendorName,
        VendorUserRole role
) {
}
