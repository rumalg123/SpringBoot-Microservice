package com.rumal.product_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record BulkPriceUpdateRequest(
        @NotEmpty(message = "items must not be empty")
        @Size(max = 50, message = "Cannot process more than 50 items per bulk request")
        List<@Valid PriceUpdateItem> items
) {
    public record PriceUpdateItem(
            @NotNull(message = "productId is required")
            UUID productId,

            @NotNull(message = "regularPrice is required")
            @DecimalMin(value = "0.01", message = "regularPrice must be greater than 0")
            BigDecimal regularPrice,

            @DecimalMin(value = "0.00", message = "discountedPrice cannot be negative")
            BigDecimal discountedPrice
    ) {}
}
