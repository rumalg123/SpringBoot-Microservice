package com.rumal.promotion_service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

public record PromotionQuoteLineRequest(
        @NotNull UUID productId,
        @NotNull UUID vendorId,
        Set<UUID> categoryIds,
        @NotNull @DecimalMin(value = "0.01", message = "unitPrice must be greater than 0") BigDecimal unitPrice,
        @Min(value = 1, message = "quantity must be at least 1") int quantity
) {
    public Set<UUID> categoryIdsOrEmpty() {
        return categoryIds == null ? Set.of() : Set.copyOf(categoryIds);
    }
}
