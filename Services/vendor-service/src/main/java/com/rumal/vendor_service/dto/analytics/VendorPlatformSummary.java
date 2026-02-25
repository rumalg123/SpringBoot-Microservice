package com.rumal.vendor_service.dto.analytics;

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
