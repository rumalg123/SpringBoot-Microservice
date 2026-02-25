package com.rumal.vendor_service.dto.analytics;

import java.math.BigDecimal;
import java.util.UUID;

public record VendorPerformanceSummary(
    UUID vendorId,
    String name,
    String status,
    BigDecimal averageRating,
    BigDecimal fulfillmentRate,
    BigDecimal disputeRate,
    BigDecimal responseTimeHours,
    int totalOrdersCompleted,
    BigDecimal commissionRate
) {}
