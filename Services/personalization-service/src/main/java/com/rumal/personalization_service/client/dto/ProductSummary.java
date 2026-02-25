package com.rumal.personalization_service.client.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ProductSummary(
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
        String approvalStatus,
        UUID vendorId,
        long viewCount,
        long soldCount,
        boolean active,
        List<VariationAttribute> variations,
        Integer stockAvailable,
        String stockStatus,
        Boolean backorderable
) {}
