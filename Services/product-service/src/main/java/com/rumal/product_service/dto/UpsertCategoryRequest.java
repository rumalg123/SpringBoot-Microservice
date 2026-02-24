package com.rumal.product_service.dto;

import com.rumal.product_service.entity.CategoryType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record UpsertCategoryRequest(
        @NotBlank(message = "name is required")
        @Size(max = 100, message = "name must be at most 100 characters")
        String name,

        @Size(max = 130, message = "slug must be at most 130 characters")
        String slug,

        @NotNull(message = "type is required")
        CategoryType type,

        UUID parentCategoryId,

        @Size(max = 1000, message = "description must be at most 1000 characters")
        String description,

        @Size(max = 500, message = "imageUrl must be at most 500 characters")
        String imageUrl,

        Integer displayOrder,

        @Valid
        List<UpsertCategoryAttributeRequest> attributes
) {}
