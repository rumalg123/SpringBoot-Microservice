package com.rumal.search_service.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public record SearchHit(
        String id,
        String slug,
        String name,
        String shortDescription,
        String mainImage,
        BigDecimal regularPrice,
        BigDecimal discountedPrice,
        BigDecimal sellingPrice,
        String sku,
        Set<String> categories,
        String mainCategory,
        Set<String> subCategories,
        String brandName,
        String vendorId,
        List<VariationEntry> variations,
        double score
) {
    public record VariationEntry(String name, String value) {}
}
