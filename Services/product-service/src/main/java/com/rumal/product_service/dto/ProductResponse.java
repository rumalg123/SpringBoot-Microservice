package com.rumal.product_service.dto;

import com.rumal.product_service.entity.ApprovalStatus;
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
        String slug,
        String shortDescription,
        String description,
        String brandName,
        List<String> images,
        String thumbnailUrl,
        BigDecimal regularPrice,
        BigDecimal discountedPrice,
        BigDecimal sellingPrice,
        UUID vendorId,
        String mainCategory,
        String mainCategorySlug,
        Set<String> subCategories,
        Set<String> subCategorySlugs,
        Set<String> categories,
        List<UUID> categoryIds,
        ProductType productType,
        boolean digital,
        List<ProductVariationAttributeResponse> variations,
        String sku,
        Integer weightGrams,
        BigDecimal lengthCm,
        BigDecimal widthCm,
        BigDecimal heightCm,
        String metaTitle,
        String metaDescription,
        ApprovalStatus approvalStatus,
        String rejectionReason,
        List<ProductSpecificationResponse> specifications,
        List<UUID> bundledProductIds,
        long viewCount,
        long soldCount,
        boolean active,
        boolean deleted,
        Instant deletedAt,
        Instant createdAt,
        Instant updatedAt,
        Integer stockAvailable,
        String stockStatus,
        Boolean backorderable
) {
    public ProductResponse withStock(Integer stockAvailable, String stockStatus, Boolean backorderable) {
        return new ProductResponse(
                id, parentProductId, name, slug, shortDescription, description,
                brandName, images, thumbnailUrl, regularPrice, discountedPrice,
                sellingPrice, vendorId, mainCategory, mainCategorySlug, subCategories,
                subCategorySlugs, categories, categoryIds, productType, digital,
                variations, sku, weightGrams, lengthCm, widthCm, heightCm,
                metaTitle, metaDescription, approvalStatus, rejectionReason,
                specifications, bundledProductIds, viewCount, soldCount, active,
                deleted, deletedAt, createdAt, updatedAt,
                stockAvailable, stockStatus, backorderable
        );
    }
}
