package com.rumal.promotion_service.service;

import com.rumal.promotion_service.dto.PromotionAnalyticsResponse;
import com.rumal.promotion_service.entity.CouponReservationStatus;
import com.rumal.promotion_service.entity.PromotionApprovalStatus;
import com.rumal.promotion_service.entity.PromotionBenefitType;
import com.rumal.promotion_service.entity.PromotionCampaign;
import com.rumal.promotion_service.entity.PromotionLifecycleStatus;
import com.rumal.promotion_service.entity.PromotionScopeType;
import com.rumal.promotion_service.exception.ResourceNotFoundException;
import com.rumal.promotion_service.repo.CouponCodeRepository;
import com.rumal.promotion_service.repo.CouponReservationRepository;
import com.rumal.promotion_service.repo.PromotionCampaignRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PromotionAnalyticsService {

    private final PromotionCampaignRepository promotionCampaignRepository;
    private final CouponCodeRepository couponCodeRepository;
    private final CouponReservationRepository couponReservationRepository;

    @Transactional(readOnly = true)
    public Page<PromotionAnalyticsResponse> list(
            Pageable pageable,
            String q,
            UUID vendorId,
            PromotionLifecycleStatus lifecycleStatus,
            PromotionApprovalStatus approvalStatus,
            PromotionScopeType scopeType,
            PromotionBenefitType benefitType
    ) {
        Instant now = Instant.now();
        return promotionCampaignRepository.findAll(buildSpec(q, vendorId, lifecycleStatus, approvalStatus, scopeType, benefitType), pageable)
                .map(promotion -> toResponse(promotion, now));
    }

    @Transactional(readOnly = true)
    public PromotionAnalyticsResponse get(UUID promotionId) {
        PromotionCampaign promotion = promotionCampaignRepository.findById(promotionId)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion not found: " + promotionId));
        return toResponse(promotion, Instant.now());
    }

    private Specification<PromotionCampaign> buildSpec(
            String q,
            UUID vendorId,
            PromotionLifecycleStatus lifecycleStatus,
            PromotionApprovalStatus approvalStatus,
            PromotionScopeType scopeType,
            PromotionBenefitType benefitType
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(q)) {
                String pattern = "%" + q.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("description")), pattern)
                ));
            }
            if (vendorId != null) {
                predicates.add(cb.equal(root.get("vendorId"), vendorId));
            }
            if (lifecycleStatus != null) {
                predicates.add(cb.equal(root.get("lifecycleStatus"), lifecycleStatus));
            }
            if (approvalStatus != null) {
                predicates.add(cb.equal(root.get("approvalStatus"), approvalStatus));
            }
            if (scopeType != null) {
                predicates.add(cb.equal(root.get("scopeType"), scopeType));
            }
            if (benefitType != null) {
                predicates.add(cb.equal(root.get("benefitType"), benefitType));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private PromotionAnalyticsResponse toResponse(PromotionCampaign promotion, Instant now) {
        UUID promotionId = promotion.getId();

        long couponCodeCount = couponCodeRepository.countByPromotion_Id(promotionId);
        long activeCouponCodeCount = couponCodeRepository.countByPromotion_IdAndActiveTrue(promotionId);

        long reservationCount = couponReservationRepository.countByPromotionId(promotionId);
        long activeReservedReservationCount = couponReservationRepository.countActiveReservedByPromotionId(promotionId, now);
        long committedReservationCount = couponReservationRepository.countByPromotionIdAndStatus(promotionId, CouponReservationStatus.COMMITTED);
        long releasedReservationCount = couponReservationRepository.countByPromotionIdAndStatus(promotionId, CouponReservationStatus.RELEASED);
        long expiredReservationCount = couponReservationRepository.countByPromotionIdAndStatus(promotionId, CouponReservationStatus.EXPIRED);

        BigDecimal activeReservedBudgetAmount = normalizeMoney(
                couponReservationRepository.sumActiveReservedDiscountByPromotionId(promotionId, now)
        );
        BigDecimal committedDiscountAmount = normalizeMoney(
                couponReservationRepository.sumReservedDiscountByPromotionIdAndStatus(promotionId, CouponReservationStatus.COMMITTED)
        );
        BigDecimal releasedDiscountAmount = normalizeMoney(
                couponReservationRepository.sumReservedDiscountByPromotionIdAndStatus(promotionId, CouponReservationStatus.RELEASED)
        );

        BigDecimal budgetAmount = normalizeNullableMoney(promotion.getBudgetAmount());
        BigDecimal burnedBudgetAmount = normalizeMoney(promotion.getBurnedBudgetAmount());
        BigDecimal remainingBudgetAmount = null;
        if (budgetAmount != null) {
            remainingBudgetAmount = normalizeMoney(budgetAmount.subtract(burnedBudgetAmount).subtract(activeReservedBudgetAmount));
            if (remainingBudgetAmount.compareTo(BigDecimal.ZERO) < 0) {
                remainingBudgetAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            }
        }

        return new PromotionAnalyticsResponse(
                promotionId,
                promotion.getName(),
                promotion.getVendorId(),
                promotion.getScopeType(),
                promotion.getApplicationLevel(),
                promotion.getBenefitType(),
                promotion.getLifecycleStatus(),
                promotion.getApprovalStatus(),
                budgetAmount,
                burnedBudgetAmount,
                activeReservedBudgetAmount,
                remainingBudgetAmount,
                couponCodeCount,
                activeCouponCodeCount,
                reservationCount,
                activeReservedReservationCount,
                committedReservationCount,
                releasedReservationCount,
                expiredReservationCount,
                committedDiscountAmount,
                releasedDiscountAmount,
                promotion.getStartsAt(),
                promotion.getEndsAt(),
                promotion.getCreatedAt(),
                promotion.getUpdatedAt()
        );
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeNullableMoney(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
