package com.rumal.payment_service.dto.analytics;

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
