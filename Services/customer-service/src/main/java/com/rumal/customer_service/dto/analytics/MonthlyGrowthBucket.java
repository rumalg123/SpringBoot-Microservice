package com.rumal.customer_service.dto.analytics;

public record MonthlyGrowthBucket(String month, long newCustomers, long totalActive) {}
