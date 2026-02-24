package com.rumal.product_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpsertProductSpecificationRequest(
        @NotBlank(message = "specification key is required")
        @Size(max = 100, message = "specification key must be at most 100 characters")
        String key,

        @NotBlank(message = "specification value is required")
        @Size(max = 500, message = "specification value must be at most 500 characters")
        String value,

        Integer displayOrder
) {}
