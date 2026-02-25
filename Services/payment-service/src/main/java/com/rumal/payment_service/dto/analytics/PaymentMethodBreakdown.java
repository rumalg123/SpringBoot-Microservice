package com.rumal.payment_service.dto.analytics;

import java.math.BigDecimal;

public record PaymentMethodBreakdown(String method, long count, BigDecimal totalAmount) {}
