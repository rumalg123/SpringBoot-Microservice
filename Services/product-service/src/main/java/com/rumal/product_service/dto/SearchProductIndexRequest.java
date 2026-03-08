package com.rumal.product_service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record SearchProductIndexRequest(
        UUID id,
        String slug,
        String name,
        String shortDescription,
        String brandName,
        String mainImage,
        BigDecimal regularPrice,
        BigDecimal discountedPrice,
        BigDecimal sellingPrice,
        String sku,
        String mainCategory,
        Set<String> subCategories,
        Set<String> categories,
        String productType,
        UUID vendorId,
        long viewCount,
        long soldCount,
        boolean active,
        Integer stockAvailable,
        String stockStatus,
        Boolean backorderable,
        List<VariationAttribute> variations,
        Instant createdAt,
        Instant updatedAt
) {
    public record VariationAttribute(
            String name,
            String value
    ) {
    }
}
