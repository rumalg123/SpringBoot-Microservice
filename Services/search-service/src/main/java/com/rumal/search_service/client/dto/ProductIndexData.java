package com.rumal.search_service.client.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ProductIndexData(
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
        List<VariationAttribute> variations,
        Instant createdAt,
        Instant updatedAt
) {}
