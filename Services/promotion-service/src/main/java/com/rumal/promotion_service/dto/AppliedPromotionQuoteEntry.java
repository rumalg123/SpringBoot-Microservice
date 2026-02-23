package com.rumal.promotion_service.dto;

import com.rumal.promotion_service.entity.PromotionApplicationLevel;
import com.rumal.promotion_service.entity.PromotionBenefitType;

import java.math.BigDecimal;
import java.util.UUID;

public record AppliedPromotionQuoteEntry(
        UUID promotionId,
        String promotionName,
        PromotionApplicationLevel applicationLevel,
        PromotionBenefitType benefitType,
        int priority,
        boolean exclusive,
        BigDecimal discountAmount
) {
}
