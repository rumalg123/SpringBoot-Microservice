package com.rumal.promotion_service.service;

import com.rumal.promotion_service.entity.CouponCode;
import com.rumal.promotion_service.entity.CouponReservationStatus;
import com.rumal.promotion_service.entity.PromotionApprovalStatus;
import com.rumal.promotion_service.entity.PromotionCampaign;
import com.rumal.promotion_service.entity.PromotionLifecycleStatus;
import com.rumal.promotion_service.repo.CouponCodeRepository;
import com.rumal.promotion_service.repo.CouponReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponValidationService {

    private final CouponCodeRepository couponCodeRepository;
    private final CouponReservationRepository couponReservationRepository;

    public Optional<CouponEligibility> findEligibleCouponForQuote(String rawCouponCode, UUID customerId, Instant now) {
        String normalized = normalizeCouponCode(rawCouponCode);
        if (normalized == null) {
            return Optional.empty();
        }
        return couponCodeRepository.findByCodeWithPromotion(normalized)
                .map(coupon -> evaluateEligibility(coupon, customerId, now));
    }

    public Optional<CouponEligibility> findEligibleCouponForReservation(String rawCouponCode, UUID customerId, Instant now) {
        String normalized = normalizeCouponCode(rawCouponCode);
        if (normalized == null) {
            return Optional.empty();
        }
        return couponCodeRepository.findByCodeWithPromotionForUpdate(normalized)
                .map(coupon -> evaluateEligibility(coupon, customerId, now));
    }

    public String normalizeCouponCode(String rawCouponCode) {
        if (!StringUtils.hasText(rawCouponCode)) {
            return null;
        }
        return rawCouponCode.trim().toUpperCase(Locale.ROOT);
    }

    private CouponEligibility evaluateEligibility(CouponCode coupon, UUID customerId, Instant now) {
        if (coupon == null) {
            return CouponEligibility.ineligible(null, "Coupon code not found");
        }
        PromotionCampaign promotion = coupon.getPromotion();
        if (promotion == null) {
            return CouponEligibility.ineligible(coupon, "Coupon is not linked to a promotion");
        }

        if (!coupon.isActive()) {
            return CouponEligibility.ineligible(coupon, "Coupon code is inactive");
        }
        if (coupon.getStartsAt() != null && coupon.getStartsAt().isAfter(now)) {
            return CouponEligibility.ineligible(coupon, "Coupon code is not active yet");
        }
        if (coupon.getEndsAt() != null && coupon.getEndsAt().isBefore(now)) {
            return CouponEligibility.ineligible(coupon, "Coupon code has expired");
        }

        if (promotion.getLifecycleStatus() != PromotionLifecycleStatus.ACTIVE) {
            return CouponEligibility.ineligible(coupon, "Promotion is not active");
        }
        PromotionApprovalStatus approvalStatus = promotion.getApprovalStatus();
        if (approvalStatus != PromotionApprovalStatus.NOT_REQUIRED && approvalStatus != PromotionApprovalStatus.APPROVED) {
            return CouponEligibility.ineligible(coupon, "Promotion is not approved");
        }
        if (promotion.getStartsAt() != null && promotion.getStartsAt().isAfter(now)) {
            return CouponEligibility.ineligible(coupon, "Promotion is not active yet");
        }
        if (promotion.getEndsAt() != null && promotion.getEndsAt().isBefore(now)) {
            return CouponEligibility.ineligible(coupon, "Promotion has expired");
        }

        if (coupon.getMaxUses() != null && coupon.getMaxUses() > 0) {
            long usedOrReserved = couponReservationRepository.countActiveOrCommittedByCouponCodeId(coupon.getId(), now);
            if (usedOrReserved >= coupon.getMaxUses()) {
                return CouponEligibility.ineligible(coupon, "Coupon usage limit reached");
            }
        }

        if (coupon.getMaxUsesPerCustomer() != null && coupon.getMaxUsesPerCustomer() > 0) {
            if (customerId == null) {
                return CouponEligibility.ineligible(coupon, "customerId is required for this coupon");
            }
            long usedOrReservedByCustomer = couponReservationRepository.countActiveOrCommittedByCouponCodeIdAndCustomerId(
                    coupon.getId(),
                    customerId,
                    now
            );
            if (usedOrReservedByCustomer >= coupon.getMaxUsesPerCustomer()) {
                return CouponEligibility.ineligible(coupon, "Per-customer coupon usage limit reached");
            }
        }

        return CouponEligibility.eligible(coupon);
    }

    public record CouponEligibility(
            boolean eligible,
            CouponCode couponCode,
            String reason
    ) {
        public static CouponEligibility eligible(CouponCode couponCode) {
            return new CouponEligibility(true, couponCode, null);
        }

        public static CouponEligibility ineligible(CouponCode couponCode, String reason) {
            return new CouponEligibility(false, couponCode, reason);
        }

        public UUID promotionId() {
            return couponCode != null && couponCode.getPromotion() != null ? couponCode.getPromotion().getId() : null;
        }
    }
}
