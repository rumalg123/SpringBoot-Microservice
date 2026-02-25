package com.rumal.analytics_service.client.dto;

public record MonthlyGrowthBucket(
        String month,
        long newCustomers,
        long totalActive
) {}
