package com.rumal.promotion_service.dto;

import com.rumal.promotion_service.entity.PromotionApplicationLevel;
import com.rumal.promotion_service.entity.PromotionApprovalStatus;
import com.rumal.promotion_service.entity.PromotionBenefitType;
import com.rumal.promotion_service.entity.PromotionFundingSource;
import com.rumal.promotion_service.entity.PromotionLifecycleStatus;
import com.rumal.promotion_service.entity.PromotionScopeType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record PromotionResponse(
        UUID id,
        String name,
        String description,
        UUID vendorId,
        PromotionScopeType scopeType,
        Set<UUID> targetProductIds,
        Set<UUID> targetCategoryIds,
        PromotionApplicationLevel applicationLevel,
        PromotionBenefitType benefitType,
        BigDecimal benefitValue,
        Integer buyQuantity,
        Integer getQuantity,
        java.util.List<PromotionSpendTierResponse> spendTiers,
        BigDecimal minimumOrderAmount,
        BigDecimal maximumDiscountAmount,
        BigDecimal budgetAmount,
        BigDecimal burnedBudgetAmount,
        BigDecimal remainingBudgetAmount,
        PromotionFundingSource fundingSource,
        boolean stackable,
        boolean exclusive,
        boolean autoApply,
        int priority,
        PromotionLifecycleStatus lifecycleStatus,
        PromotionApprovalStatus approvalStatus,
        String approvalNote,
        Instant startsAt,
        Instant endsAt,
        String createdByUserSub,
        String updatedByUserSub,
        String submittedByUserSub,
        String approvedByUserSub,
        String rejectedByUserSub,
        Instant submittedAt,
        Instant approvedAt,
        Instant rejectedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
