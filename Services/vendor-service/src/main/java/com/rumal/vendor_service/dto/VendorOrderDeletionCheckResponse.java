package com.rumal.vendor_service.dto;

import java.time.Instant;
import java.util.UUID;

public record VendorOrderDeletionCheckResponse(
        UUID vendorId,
        long totalOrders,
        long pendingOrders,
        Instant lastOrderAt
) {
}
