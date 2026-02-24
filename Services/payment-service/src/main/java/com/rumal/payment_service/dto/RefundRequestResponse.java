package com.rumal.payment_service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RefundRequestResponse(
        UUID id,
        UUID paymentId,
        UUID orderId,
        UUID vendorOrderId,
        UUID vendorId,
        UUID customerId,
        BigDecimal refundAmount,
        String currency,
        String customerReason,
        String vendorResponseNote,
        String adminNote,
        String status,
        Instant vendorResponseDeadline,
        String payhereRefundRef,
        Instant vendorRespondedAt,
        Instant adminFinalizedAt,
        Instant refundCompletedAt,
        Instant createdAt
) {}
