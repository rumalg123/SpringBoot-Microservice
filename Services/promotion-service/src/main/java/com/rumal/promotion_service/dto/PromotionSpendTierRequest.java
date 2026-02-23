package com.rumal.promotion_service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PromotionSpendTierRequest(
        @NotNull @DecimalMin(value = "0.01", message = "thresholdAmount must be greater than 0") BigDecimal thresholdAmount,
        @NotNull @DecimalMin(value = "0.01", message = "discountAmount must be greater than 0") BigDecimal discountAmount
) {
}
