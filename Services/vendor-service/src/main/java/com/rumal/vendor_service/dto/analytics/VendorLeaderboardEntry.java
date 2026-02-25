package com.rumal.vendor_service.dto.analytics;

import java.math.BigDecimal;
import java.util.UUID;

public record VendorLeaderboardEntry(
    UUID id,
    String name,
    int totalOrdersCompleted,
    BigDecimal averageRating,
    BigDecimal fulfillmentRate,
    BigDecimal disputeRate,
    boolean verified
) {}
