package com.rumal.analytics_service.client.dto;

import java.math.BigDecimal;

public record PaymentMethodBreakdown(
        String method,
        long count,
        BigDecimal totalAmount
) {}
