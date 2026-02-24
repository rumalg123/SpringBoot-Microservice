package com.rumal.payment_service.dto;

import java.time.Instant;
import java.util.UUID;

public record PaymentAuditResponse(
        UUID id,
        UUID paymentId,
        UUID refundRequestId,
        UUID payoutId,
        String eventType,
        String fromStatus,
        String toStatus,
        String actorType,
        String actorId,
        String note,
        Instant createdAt
) {}
