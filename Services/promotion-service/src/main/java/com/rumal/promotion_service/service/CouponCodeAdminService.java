package com.rumal.promotion_service.service;

import com.rumal.promotion_service.dto.CouponCodeResponse;
import com.rumal.promotion_service.dto.CreateCouponCodeRequest;
import com.rumal.promotion_service.entity.CouponCode;
import com.rumal.promotion_service.entity.PromotionCampaign;
import com.rumal.promotion_service.exception.ResourceNotFoundException;
import com.rumal.promotion_service.exception.ValidationException;
import com.rumal.promotion_service.repo.CouponCodeRepository;
import com.rumal.promotion_service.repo.PromotionCampaignRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CouponCodeAdminService {

    private final CouponCodeRepository couponCodeRepository;
    private final PromotionCampaignRepository promotionCampaignRepository;

    @Transactional(readOnly = true)
    public List<CouponCodeResponse> listByPromotion(UUID promotionId) {
        requirePromotion(promotionId);
        return couponCodeRepository.findByPromotionIdOrderByCreatedAtDesc(promotionId).stream()
                .map(this::toResponse)
                .toList();
    }

    @CacheEvict(cacheNames = "promotionAdminList", allEntries = true)
    @Transactional
    public CouponCodeResponse create(UUID promotionId, CreateCouponCodeRequest request, String actorUserSub) {
        PromotionCampaign promotion = requirePromotion(promotionId);

        String normalizedCode = normalizeCode(request.code());
        if (couponCodeRepository.existsByCodeIgnoreCase(normalizedCode)) {
            throw new ValidationException("Coupon code already exists");
        }

        CouponCode coupon = CouponCode.builder()
                .promotion(promotion)
                .code(normalizedCode)
                .active(request.active() == null || request.active())
                .maxUses(request.maxUses())
                .maxUsesPerCustomer(request.maxUsesPerCustomer())
                .reservationTtlSeconds(request.reservationTtlSeconds())
                .startsAt(request.startsAt())
                .endsAt(request.endsAt())
                .createdByUserSub(trimToNull(actorUserSub))
                .updatedByUserSub(trimToNull(actorUserSub))
                .build();

        return toResponse(couponCodeRepository.save(coupon));
    }

    private PromotionCampaign requirePromotion(UUID promotionId) {
        return promotionCampaignRepository.findById(promotionId)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion not found: " + promotionId));
    }

    private String normalizeCode(String code) {
        if (!StringUtils.hasText(code)) {
            throw new ValidationException("Coupon code is required");
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > 64) {
            throw new ValidationException("Coupon code must be 64 characters or less");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private CouponCodeResponse toResponse(CouponCode coupon) {
        return new CouponCodeResponse(
                coupon.getId(),
                coupon.getPromotion() == null ? null : coupon.getPromotion().getId(),
                coupon.getCode(),
                coupon.isActive(),
                coupon.getMaxUses(),
                coupon.getMaxUsesPerCustomer(),
                coupon.getReservationTtlSeconds(),
                coupon.getStartsAt(),
                coupon.getEndsAt(),
                coupon.getCreatedByUserSub(),
                coupon.getUpdatedByUserSub(),
                coupon.getCreatedAt(),
                coupon.getUpdatedAt()
        );
    }
}
