package com.rumal.poster_service.dto;

import java.time.Instant;
import java.util.UUID;

public record PosterVariantResponse(
        UUID id,
        UUID posterId,
        String variantName,
        int weight,
        String desktopImage,
        String mobileImage,
        String tabletImage,
        String srcsetDesktop,
        String srcsetMobile,
        String srcsetTablet,
        String linkUrl,
        long impressions,
        long clicks,
        double clickThroughRate,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
