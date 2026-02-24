package com.rumal.poster_service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpsertPosterVariantRequest(
        @NotBlank @Size(max = 160) String variantName,
        @Min(1) @Max(100) Integer weight,
        @Size(max = 260) String desktopImage,
        @Size(max = 260) String mobileImage,
        @Size(max = 260) String tabletImage,
        @Size(max = 1000) String srcsetDesktop,
        @Size(max = 1000) String srcsetMobile,
        @Size(max = 1000) String srcsetTablet,
        @Size(max = 500) String linkUrl,
        Boolean active
) {
}
