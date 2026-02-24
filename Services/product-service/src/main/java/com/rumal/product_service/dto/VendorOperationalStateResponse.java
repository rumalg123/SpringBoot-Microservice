package com.rumal.product_service.dto;

import com.rumal.product_service.entity.ProductType;

import java.util.UUID;

public record VendorOperationalStateResponse(
        UUID vendorId,
        String vendorName,
        boolean active,
        boolean deleted,
        String status,
        boolean acceptingOrders,
        boolean storefrontVisible
) {
}
