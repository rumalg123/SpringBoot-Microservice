package com.rumal.customer_service.dto.analytics;

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
