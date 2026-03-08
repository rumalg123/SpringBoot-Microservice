package com.rumal.personalization_service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record QueuedEventPayload(
        String eventId,
        UUID userId,
        String sessionId,
        EventType eventType,
        UUID productId,
        String categorySlugs,
        UUID vendorId,
        String brandName,
        BigDecimal price,
        String metadata,
        Instant enqueuedAt
) {
}
