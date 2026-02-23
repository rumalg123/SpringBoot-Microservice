package com.rumal.cart_service.dto;

import java.util.UUID;

public record VendorOperationalStateResponse(
        UUID vendorId,
        boolean active,
        boolean deleted,
        String status,
        boolean acceptingOrders,
        boolean storefrontVisible
) {
}
