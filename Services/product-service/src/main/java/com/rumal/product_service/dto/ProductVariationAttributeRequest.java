package com.rumal.product_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductVariationAttributeRequest(
        @NotBlank(message = "variation name is required")
        @Size(max = 60, message = "variation name must be at most 60 characters")
        String name,

        @Size(max = 100, message = "variation value must be at most 100 characters")
        String value
) {}
