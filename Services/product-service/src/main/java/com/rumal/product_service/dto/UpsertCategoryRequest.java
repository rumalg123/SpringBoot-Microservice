package com.rumal.product_service.dto;

import com.rumal.product_service.entity.CategoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpsertCategoryRequest(
        @NotBlank(message = "name is required")
        @Size(max = 100, message = "name must be at most 100 characters")
        String name,

        @Size(max = 130, message = "slug must be at most 130 characters")
        String slug,

        @NotNull(message = "type is required")
        CategoryType type,

        UUID parentCategoryId
) {}
