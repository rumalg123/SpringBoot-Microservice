package com.rumal.analytics_service.client.dto;

import java.time.Instant;
import java.util.UUID;

public record CustomerProfileSummary(
        UUID id,
        String name,
        String email,
        String loyaltyTier,
        int loyaltyPoints,
        Instant memberSince,
        boolean active
) {}
