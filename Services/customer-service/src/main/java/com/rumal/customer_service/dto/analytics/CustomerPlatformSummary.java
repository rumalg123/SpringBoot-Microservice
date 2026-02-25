package com.rumal.customer_service.dto.analytics;

import java.util.Map;

public record CustomerPlatformSummary(
    long totalCustomers,
    long activeCustomers,
    long newCustomersThisMonth,
    Map<String, Long> loyaltyDistribution
) {}
