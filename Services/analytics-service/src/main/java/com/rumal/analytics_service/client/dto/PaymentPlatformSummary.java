package com.rumal.analytics_service.client.dto;

import java.math.BigDecimal;

public record PaymentPlatformSummary(
        long totalPayments,
        long successfulPayments,
        long failedPayments,
        BigDecimal totalSuccessAmount,
        BigDecimal totalRefundAmount,
        long chargebackCount,
        BigDecimal avgPaymentAmount
) {}
