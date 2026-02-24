package com.rumal.product_service.dto;

import com.rumal.product_service.entity.CategoryType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        String slug,
        CategoryType type,
        UUID parentCategoryId,
        String description,
        String imageUrl,
        Integer depth,
        String path,
        Integer displayOrder,
        List<CategoryAttributeResponse> attributes,
        boolean deleted,
        Instant deletedAt,
        Instant createdAt,
        Instant updatedAt
) {}
