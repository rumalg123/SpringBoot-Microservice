package com.rumal.promotion_service.service;

import com.rumal.promotion_service.dto.CommitCouponReservationRequest;
import com.rumal.promotion_service.dto.CouponReservationResponse;
import com.rumal.promotion_service.dto.CreateCouponReservationRequest;
import com.rumal.promotion_service.dto.PromotionQuoteRequest;
import com.rumal.promotion_service.dto.PromotionQuoteResponse;
import com.rumal.promotion_service.dto.ReleaseCouponReservationRequest;
import com.rumal.promotion_service.entity.CouponCode;
import com.rumal.promotion_service.entity.CouponReservation;
import com.rumal.promotion_service.entity.CouponReservationStatus;
import com.rumal.promotion_service.exception.ResourceNotFoundException;
import com.rumal.promotion_service.exception.ValidationException;
import com.rumal.promotion_service.repo.CouponReservationRepository;
import com.rumal.promotion_service.repo.PromotionCampaignRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CouponReservationService {

    private final CouponValidationService couponValidationService;
    private final CouponReservationRepository couponReservationRepository;
    private final PromotionCampaignRepository promotionCampaignRepository;
    private final PromotionQuoteService promotionQuoteService;

    @Value("${coupon.reservation.default-ttl-seconds:900}")
    private int defaultReservationTtlSeconds;

    @Value("${coupon.reservation.max-ttl-seconds:1800}")
    private int maxReservationTtlSeconds;

    @Transactional
    public CouponReservationResponse reserve(CreateCouponReservationRequest request) {
        validateReservationRequest(request);

        if (StringUtils.hasText(request.requestKey())) {
            Optional<CouponReservation> existing = couponReservationRepository.findByRequestKey(request.requestKey().trim());
            if (existing.isPresent()) {
                CouponReservation reservation = existing.get();
                if (!reservation.getCustomerId().equals(request.customerId())
                        || !reservation.getCouponCodeText().equalsIgnoreCase(request.couponCode())) {
                    throw new ValidationException("requestKey is already used for a different coupon reservation");
                }
                return toResponse(reservation, null);
            }
        }

        Instant now = Instant.now();
        PromotionQuoteResponse quote = promotionQuoteService.quote(request.quoteRequest());
        CouponValidationService.CouponEligibility eligibility = couponValidationService
                .findEligibleCouponForReservation(request.couponCode(), request.customerId(), now)
                .orElseThrow(() -> new ValidationException("Coupon code not found"));
        if (!eligibility.eligible()) {
            throw new ValidationException(eligibility.reason());
        }

        CouponCode couponCode = eligibility.couponCode();
        UUID couponPromotionId = eligibility.promotionId();
        if (couponPromotionId == null) {
            throw new ValidationException("Coupon promotion link is invalid");
        }

        BigDecimal couponDiscount = quote.appliedPromotions().stream()
                .filter(entry -> couponPromotionId.equals(entry.promotionId()))
                .map(entry -> entry.discountAmount() == null ? BigDecimal.ZERO : entry.discountAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        if (couponDiscount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Coupon code is valid but produced no discount for the provided quote");
        }

        assertBudgetCanReserve(couponPromotionId, couponDiscount, now);

        int ttlSeconds = resolveReservationTtlSeconds(couponCode);
        CouponReservation reservation = CouponReservation.builder()
                .couponCode(couponCode)
                .promotionId(couponPromotionId)
                .customerId(request.customerId())
                .couponCodeText(couponCode.getCode())
                .requestKey(StringUtils.hasText(request.requestKey()) ? request.requestKey().trim() : null)
                .status(CouponReservationStatus.RESERVED)
                .reservedDiscountAmount(couponDiscount)
                .quotedSubtotal(normalizeMoney(quote.subtotal()))
                .quotedGrandTotal(normalizeMoney(quote.grandTotal()))
                .reservedAt(now)
                .expiresAt(now.plusSeconds(ttlSeconds))
                .build();

        return toResponse(couponReservationRepository.save(reservation), quote);
    }

    @Transactional
    public CouponReservationResponse commit(UUID reservationId, CommitCouponReservationRequest request) {
        CouponReservation reservation = couponReservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon reservation not found: " + reservationId));

        if (reservation.getStatus() == CouponReservationStatus.COMMITTED) {
            if (reservation.getOrderId() != null && !reservation.getOrderId().equals(request.orderId())) {
                throw new ValidationException("Reservation already committed to a different order");
            }
            return toResponse(reservation, null);
        }
        if (reservation.getStatus() == CouponReservationStatus.RELEASED) {
            throw new ValidationException("Released reservation cannot be committed");
        }
        expireIfNeeded(reservation, Instant.now());
        if (reservation.getStatus() == CouponReservationStatus.EXPIRED) {
            throw new ValidationException("Reservation has expired");
        }

        incrementPromotionBurnedBudgetIfApplicable(reservation.getPromotionId(), reservation.getReservedDiscountAmount());
        reservation.setStatus(CouponReservationStatus.COMMITTED);
        reservation.setOrderId(request.orderId());
        reservation.setCommittedAt(Instant.now());
        return toResponse(couponReservationRepository.save(reservation), null);
    }

    @Transactional
    public CouponReservationResponse release(UUID reservationId, ReleaseCouponReservationRequest request) {
        CouponReservation reservation = couponReservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon reservation not found: " + reservationId));

        if (reservation.getStatus() == CouponReservationStatus.RELEASED) {
            return toResponse(reservation, null);
        }
        expireIfNeeded(reservation, Instant.now());
        if (reservation.getStatus() == CouponReservationStatus.EXPIRED) {
            return toResponse(couponReservationRepository.save(reservation), null);
        }

        if (reservation.getStatus() == CouponReservationStatus.COMMITTED) {
            decrementPromotionBurnedBudgetIfApplicable(reservation.getPromotionId(), reservation.getReservedDiscountAmount());
        }
        reservation.setStatus(CouponReservationStatus.RELEASED);
        reservation.setReleasedAt(Instant.now());
        reservation.setReleaseReason(request.reason().trim());
        return toResponse(couponReservationRepository.save(reservation), null);
    }

    private void validateReservationRequest(CreateCouponReservationRequest request) {
        if (request == null) {
            throw new ValidationException("Reservation request is required");
        }
        PromotionQuoteRequest quoteRequest = request.quoteRequest();
        if (quoteRequest == null) {
            throw new ValidationException("quoteRequest is required");
        }
        if (!request.customerId().equals(quoteRequest.customerId())) {
            throw new ValidationException("quoteRequest.customerId must match reservation customerId");
        }
        if (!StringUtils.hasText(quoteRequest.couponCode())) {
            throw new ValidationException("quoteRequest.couponCode is required");
        }
        if (!request.couponCode().trim().equalsIgnoreCase(quoteRequest.couponCode().trim())) {
            throw new ValidationException("quoteRequest.couponCode must match reservation couponCode");
        }
    }

    private int resolveReservationTtlSeconds(CouponCode couponCode) {
        int ttl = couponCode.getReservationTtlSeconds() == null ? defaultReservationTtlSeconds : couponCode.getReservationTtlSeconds();
        ttl = Math.max(60, ttl);
        return Math.min(Math.max(60, maxReservationTtlSeconds), ttl);
    }

    private void expireIfNeeded(CouponReservation reservation, Instant now) {
        if (reservation.getStatus() == CouponReservationStatus.RESERVED
                && reservation.getExpiresAt() != null
                && !reservation.getExpiresAt().isAfter(now)) {
            reservation.setStatus(CouponReservationStatus.EXPIRED);
        }
    }

    private CouponReservationResponse toResponse(CouponReservation reservation, PromotionQuoteResponse quote) {
        return new CouponReservationResponse(
                reservation.getId(),
                reservation.getCouponCode() == null ? null : reservation.getCouponCode().getId(),
                reservation.getPromotionId(),
                reservation.getCouponCodeText(),
                reservation.getStatus() == null ? null : reservation.getStatus().name(),
                reservation.getCustomerId(),
                reservation.getOrderId(),
                normalizeMoney(reservation.getReservedDiscountAmount()),
                normalizeMoney(reservation.getQuotedSubtotal()),
                normalizeMoney(reservation.getQuotedGrandTotal()),
                reservation.getReservedAt(),
                reservation.getExpiresAt(),
                reservation.getCommittedAt(),
                reservation.getReleasedAt(),
                reservation.getReleaseReason(),
                quote
        );
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private void assertBudgetCanReserve(UUID promotionId, BigDecimal requestedDiscount, Instant now) {
        if (promotionId == null) {
            return;
        }
        var promotion = promotionCampaignRepository.findByIdForUpdate(promotionId)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion not found: " + promotionId));
        if (promotion.getBudgetAmount() == null) {
            return;
        }

        BigDecimal budget = normalizeMoney(promotion.getBudgetAmount());
        BigDecimal burned = normalizeMoney(promotion.getBurnedBudgetAmount());
        BigDecimal activeReserved = normalizeMoney(couponReservationRepository.sumActiveReservedDiscountByPromotionId(promotionId, now));
        BigDecimal remaining = normalizeMoney(budget.subtract(burned).subtract(activeReserved));
        if (remaining.compareTo(normalizeMoney(requestedDiscount)) < 0) {
            throw new ValidationException("Campaign budget remaining is insufficient for this reservation");
        }
    }

    private void incrementPromotionBurnedBudgetIfApplicable(UUID promotionId, BigDecimal amount) {
        if (promotionId == null) {
            return;
        }
        var promotion = promotionCampaignRepository.findByIdForUpdate(promotionId)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion not found: " + promotionId));
        if (promotion.getBudgetAmount() == null) {
            return;
        }

        BigDecimal increment = normalizeMoney(amount);
        BigDecimal newBurned = normalizeMoney(normalizeMoney(promotion.getBurnedBudgetAmount()).add(increment));
        BigDecimal budget = normalizeMoney(promotion.getBudgetAmount());
        if (newBurned.compareTo(budget) > 0) {
            throw new ValidationException("Campaign budget exhausted before reservation commit");
        }
        promotion.setBurnedBudgetAmount(newBurned);
        promotionCampaignRepository.save(promotion);
    }

    private void decrementPromotionBurnedBudgetIfApplicable(UUID promotionId, BigDecimal amount) {
        if (promotionId == null) {
            return;
        }
        var promotion = promotionCampaignRepository.findByIdForUpdate(promotionId)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion not found: " + promotionId));
        if (promotion.getBudgetAmount() == null) {
            return;
        }

        BigDecimal decrement = normalizeMoney(amount);
        BigDecimal burned = normalizeMoney(promotion.getBurnedBudgetAmount());
        BigDecimal newBurned = normalizeMoney(burned.subtract(decrement));
        if (newBurned.compareTo(BigDecimal.ZERO) < 0) {
            newBurned = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        promotion.setBurnedBudgetAmount(newBurned);
        promotionCampaignRepository.save(promotion);
    }
}
