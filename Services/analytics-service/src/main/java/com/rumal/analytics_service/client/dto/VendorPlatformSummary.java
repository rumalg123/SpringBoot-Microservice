package com.rumal.analytics_service.client.dto;

import java.math.BigDecimal;

public record VendorPlatformSummary(
        long totalVendors,
        long activeVendors,
        long pendingVendors,
        long suspendedVendors,
        long verifiedVendors,
        BigDecimal avgCommissionRate,
        BigDecimal avgFulfillmentRate
) {}
