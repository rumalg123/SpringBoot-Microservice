package com.rumal.promotion_service.dto;

import com.rumal.promotion_service.entity.PromotionApplicationLevel;
import com.rumal.promotion_service.entity.PromotionBenefitType;
import com.rumal.promotion_service.entity.PromotionScopeType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record PublicPromotionResponse(
        UUID id,
        String name,
        String description,
        PromotionScopeType scopeType,
        PromotionApplicationLevel applicationLevel,
        PromotionBenefitType benefitType,
        BigDecimal benefitValue,
        Integer buyQuantity,
        Integer getQuantity,
        List<PromotionSpendTierResponse> spendTiers,
        BigDecimal minimumOrderAmount,
        BigDecimal maximumDiscountAmount,
        boolean stackable,
        String stackingGroup,
        boolean autoApply,
        Set<UUID> targetProductIds,
        Set<UUID> targetCategoryIds,
        boolean flashSale,
        Instant flashSaleStartAt,
        Instant flashSaleEndAt,
        Instant startsAt,
        Instant endsAt
) {
}
