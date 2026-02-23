package com.rumal.vendor_service.dto;

import com.rumal.vendor_service.entity.VendorStatus;

import java.util.UUID;

public record VendorOperationalStateResponse(
        UUID vendorId,
        boolean active,
        boolean deleted,
        VendorStatus status,
        boolean acceptingOrders,
        boolean storefrontVisible
) {
}
