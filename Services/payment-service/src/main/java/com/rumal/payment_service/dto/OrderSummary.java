package com.rumal.payment_service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderSummary(
        UUID id,
        UUID customerId,
        String item,
        int quantity,
        BigDecimal orderTotal,
        String currency,
        String status,
        String paymentId,
        Instant expiresAt
) {}
