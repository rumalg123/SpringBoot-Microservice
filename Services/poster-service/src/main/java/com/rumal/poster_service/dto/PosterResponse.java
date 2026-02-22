package com.rumal.poster_service.dto;

import com.rumal.poster_service.entity.PosterLinkType;
import com.rumal.poster_service.entity.PosterPlacement;
import com.rumal.poster_service.entity.PosterSize;

import java.time.Instant;
import java.util.UUID;

public record PosterResponse(
        UUID id,
        String name,
        String slug,
        PosterPlacement placement,
        PosterSize size,
        String desktopImage,
        String mobileImage,
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
        boolean deleted,
        Instant deletedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
