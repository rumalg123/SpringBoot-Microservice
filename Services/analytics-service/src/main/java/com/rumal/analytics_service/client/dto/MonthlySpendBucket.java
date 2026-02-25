package com.rumal.analytics_service.client.dto;

import java.math.BigDecimal;

public record MonthlySpendBucket(
        String month,
        BigDecimal amount,
        long orderCount
) {}
