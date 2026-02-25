package com.rumal.product_service.dto;

import com.rumal.product_service.entity.ApprovalStatus;
import com.rumal.product_service.entity.ProductType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ProductSummaryResponse(
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
        ProductType productType,
        ApprovalStatus approvalStatus,
        UUID vendorId,
        long viewCount,
        long soldCount,
        boolean active,
        List<ProductVariationAttributeResponse> variations,
        Instant createdAt,
        Instant updatedAt,
        Integer stockAvailable,
        String stockStatus,
        Boolean backorderable
) {
    public ProductSummaryResponse withStock(Integer stockAvailable, String stockStatus, Boolean backorderable) {
        return new ProductSummaryResponse(
                id, slug, name, shortDescription, brandName, mainImage,
                regularPrice, discountedPrice, sellingPrice, sku, mainCategory,
                subCategories, categories, productType, approvalStatus, vendorId,
                viewCount, soldCount, active, variations,
                createdAt, updatedAt,
                stockAvailable, stockStatus, backorderable
        );
    }
}
