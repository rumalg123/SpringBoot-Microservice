package com.rumal.promotion_service.dto;

import com.rumal.promotion_service.entity.PromotionApplicationLevel;
import com.rumal.promotion_service.entity.PromotionApprovalStatus;
import com.rumal.promotion_service.entity.PromotionBenefitType;
import com.rumal.promotion_service.entity.PromotionLifecycleStatus;
import com.rumal.promotion_service.entity.PromotionScopeType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PromotionAnalyticsResponse(
        UUID promotionId,
        String name,
        UUID vendorId,
        PromotionScopeType scopeType,
        PromotionApplicationLevel applicationLevel,
        PromotionBenefitType benefitType,
        PromotionLifecycleStatus lifecycleStatus,
        PromotionApprovalStatus approvalStatus,
        BigDecimal budgetAmount,
        BigDecimal burnedBudgetAmount,
        BigDecimal activeReservedBudgetAmount,
        BigDecimal remainingBudgetAmount,
        long couponCodeCount,
        long activeCouponCodeCount,
        long reservationCount,
        long activeReservedReservationCount,
        long committedReservationCount,
        long releasedReservationCount,
        long expiredReservationCount,
        BigDecimal committedDiscountAmount,
        BigDecimal releasedDiscountAmount,
        Instant startsAt,
        Instant endsAt,
        Instant createdAt,
        Instant updatedAt
) {
}
