package com.rumal.payment_service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentDetailResponse(
        UUID id,
        UUID orderId,
        UUID customerId,
        String customerKeycloakId,
        BigDecimal amount,
        String currency,
        String status,
        Integer payhereStatusCode,
        String statusMessage,
        String payherePaymentId,
        String paymentMethod,
        String cardHolderName,
        String cardNoMasked,
        String cardExpiry,
        String itemsDescription,
        Instant paidAt,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt
) {}
