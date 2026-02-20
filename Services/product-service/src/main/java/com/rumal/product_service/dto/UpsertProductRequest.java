package com.rumal.product_service.dto;

import com.rumal.product_service.entity.ProductType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record UpsertProductRequest(
        @NotBlank(message = "name is required")
        @Size(max = 150, message = "name must be at most 150 characters")
        String name,

        @Size(max = 180, message = "slug must be at most 180 characters")
        String slug,

        @NotBlank(message = "shortDescription is required")
        @Size(max = 300, message = "shortDescription must be at most 300 characters")
        String shortDescription,

        @NotBlank(message = "description is required")
        @Size(max = 4000, message = "description must be at most 4000 characters")
        String description,

        @NotEmpty(message = "images must contain at least one item")
        @Size(max = 5, message = "images cannot contain more than 5 items")
        List<@NotBlank(message = "image name cannot be blank") String> images,

        @NotNull(message = "regularPrice is required")
        @DecimalMin(value = "0.01", message = "regularPrice must be greater than 0")
        BigDecimal regularPrice,

        @DecimalMin(value = "0.00", message = "discountedPrice cannot be negative")
        BigDecimal discountedPrice,

        UUID vendorId,

        Set<@NotBlank(message = "category cannot be blank") String> categories,

        @NotNull(message = "productType is required")
        ProductType productType,

        @Valid
        List<ProductVariationAttributeRequest> variations,

        @NotBlank(message = "sku is required")
        @Size(max = 80, message = "sku must be at most 80 characters")
        String sku,
        Boolean active
) {}
