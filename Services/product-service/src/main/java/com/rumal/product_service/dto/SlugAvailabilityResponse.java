package com.rumal.product_service.dto;

public record SlugAvailabilityResponse(
        String slug,
        boolean available
) {}
