package com.rumal.poster_service.dto;

import com.rumal.poster_service.entity.PosterPlacement;

import java.time.Instant;
import java.util.UUID;

public record PosterAnalyticsResponse(
        UUID id,
        String name,
        String slug,
        PosterPlacement placement,
        long clickCount,
        long impressionCount,
        double clickThroughRate,
        Instant lastClickAt,
        Instant lastImpressionAt,
        Instant createdAt
) {
}
