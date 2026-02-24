package com.rumal.product_service.dto;

import com.rumal.product_service.entity.ApprovalStatus;
import com.rumal.product_service.entity.ProductType;

import java.math.BigDecimal;
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
        List<ProductVariationAttributeResponse> variations
) {}
