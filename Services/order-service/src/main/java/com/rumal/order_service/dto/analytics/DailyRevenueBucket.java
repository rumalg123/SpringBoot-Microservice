package com.rumal.order_service.dto.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyRevenueBucket(LocalDate date, BigDecimal revenue, long orderCount) {}
