package com.rumal.analytics_service.client.dto;

import java.util.Map;

public record CustomerPlatformSummary(
        long totalCustomers,
        long activeCustomers,
        long newCustomersThisMonth,
        Map<String, Long> loyaltyDistribution
) {}
