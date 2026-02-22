package com.rumal.poster_service.dto;

import com.rumal.poster_service.entity.PosterLinkType;
import com.rumal.poster_service.entity.PosterPlacement;
import com.rumal.poster_service.entity.PosterSize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record UpsertPosterRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 180) String slug,
        @NotNull PosterPlacement placement,
        @NotNull PosterSize size,
        @NotBlank @Size(max = 260) String desktopImage,
        @Size(max = 260) String mobileImage,
        @NotNull PosterLinkType linkType,
        @Size(max = 500) String linkTarget,
        Boolean openInNewTab,
        @Size(max = 120) String title,
        @Size(max = 250) String subtitle,
        @Size(max = 60) String ctaLabel,
        @Size(max = 40) String backgroundColor,
        Integer sortOrder,
        Boolean active,
        Instant startAt,
        Instant endAt
) {
}
