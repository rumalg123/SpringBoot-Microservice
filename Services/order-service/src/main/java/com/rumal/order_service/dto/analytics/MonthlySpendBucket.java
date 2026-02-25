package com.rumal.order_service.dto.analytics;

import java.math.BigDecimal;

public record MonthlySpendBucket(String month, BigDecimal amount, long orderCount) {}
