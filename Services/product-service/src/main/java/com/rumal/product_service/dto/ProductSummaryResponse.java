package com.rumal.product_service.dto;

import com.rumal.product_service.entity.ProductType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ProductSummaryResponse(
        UUID id,
        String name,
        String shortDescription,
        String mainImage,
        BigDecimal regularPrice,
        BigDecimal discountedPrice,
        BigDecimal sellingPrice,
        String sku,
        Set<String> categories,
        ProductType productType,
        UUID vendorId,
        boolean active,
        List<ProductVariationAttributeResponse> variations
) {}

