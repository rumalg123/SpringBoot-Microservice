package com.rumal.promotion_service.dto;

import java.math.BigDecimal;

public record PromotionSpendTierResponse(
        BigDecimal thresholdAmount,
        BigDecimal discountAmount
) {
}
