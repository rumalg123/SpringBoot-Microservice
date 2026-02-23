package com.rumal.vendor_service.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record VendorDeletionEligibilityResponse(
        UUID vendorId,
        boolean eligible,
        long totalOrders,
        long pendingOrders,
        Instant lastOrderAt,
        Instant refundHoldUntil,
        List<String> blockingReasons
) {
}
