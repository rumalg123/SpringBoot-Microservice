package com.rumal.promotion_service.dto;

import java.time.Instant;
import java.util.UUID;

public record CustomerSummary(
        UUID id,
        String name,
        String email,
        String loyaltyTier,
        Instant createdAt
) {
}
