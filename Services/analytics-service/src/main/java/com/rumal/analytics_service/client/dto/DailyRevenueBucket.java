package com.rumal.analytics_service.client.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyRevenueBucket(
        LocalDate date,
        BigDecimal revenue,
        long orderCount
) {}
