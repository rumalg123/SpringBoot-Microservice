package com.rumal.poster_service.dto;

import com.rumal.poster_service.entity.PosterLinkType;
import com.rumal.poster_service.entity.PosterPlacement;
import com.rumal.poster_service.entity.PosterSize;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record PosterResponse(
        UUID id,
        String name,
        String slug,
        PosterPlacement placement,
        PosterSize size,
        String desktopImage,
        String mobileImage,
        String tabletImage,
        String srcsetDesktop,
        String srcsetMobile,
        String srcsetTablet,
        PosterLinkType linkType,
        String linkTarget,
        boolean openInNewTab,
        String title,
        String subtitle,
        String ctaLabel,
        String backgroundColor,
        int sortOrder,
        boolean active,
        Instant startAt,
        Instant endAt,
        long clickCount,
        long impressionCount,
        Instant lastClickAt,
        Instant lastImpressionAt,
        Set<String> targetCountries,
        String targetCustomerSegment,
        boolean deleted,
        Instant deletedAt,
        Instant createdAt,
        Instant updatedAt,
        PosterVariantResponse selectedVariant,
        List<PosterVariantResponse> variants
) {
}
