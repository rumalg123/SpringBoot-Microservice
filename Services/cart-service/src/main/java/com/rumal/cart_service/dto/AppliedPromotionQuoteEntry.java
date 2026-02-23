package com.rumal.cart_service.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AppliedPromotionQuoteEntry(
        UUID promotionId,
        String promotionName,
        String applicationLevel,
        String benefitType,
        int priority,
        boolean exclusive,
        BigDecimal discountAmount
) {
}
