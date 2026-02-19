package com.rumal.product_service.dto;

import com.rumal.product_service.entity.ProductType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        UUID parentProductId,
        String name,
        String shortDescription,
        String description,
        List<String> images,
        BigDecimal regularPrice,
        BigDecimal discountedPrice,
        BigDecimal sellingPrice,
        UUID vendorId,
        String mainCategory,
        Set<String> subCategories,
        Set<String> categories,
        ProductType productType,
        List<ProductVariationAttributeResponse> variations,
        String sku,
        boolean active,
        boolean deleted,
        Instant deletedAt,
        Instant createdAt,
        Instant updatedAt
) {}
