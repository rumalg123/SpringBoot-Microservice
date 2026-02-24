package com.rumal.payment_service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID orderId,
        UUID customerId,
        BigDecimal amount,
        String currency,
        String status,
        String paymentMethod,
        String cardNoMasked,
        String payherePaymentId,
        Instant paidAt,
        Instant createdAt
) {}
