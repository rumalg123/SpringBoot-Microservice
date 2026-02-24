package com.rumal.promotion_service.service;

import com.rumal.promotion_service.dto.BatchCreateCouponsRequest;
import com.rumal.promotion_service.dto.BatchCreateCouponsResponse;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
public class CouponCodeAdminService {

    private final CouponCodeRepository couponCodeRepository;
    private final PromotionCampaignRepository promotionCampaignRepository;

    public Page<CouponCodeResponse> listByPromotion(UUID promotionId, Pageable pageable) {
        requirePromotion(promotionId);
        return couponCodeRepository.findByPromotionId(promotionId, pageable)
                .map(this::toResponse);
    }

    @CacheEvict(cacheNames = "promotionAdminList", allEntries = true)
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
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

    @CacheEvict(cacheNames = "promotionAdminList", allEntries = true)
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 30)
    public BatchCreateCouponsResponse batchCreate(UUID promotionId, BatchCreateCouponsRequest request, String actorUserSub) {
        PromotionCampaign promotion = requirePromotion(promotionId);
        String prefix = request.prefix().trim().toUpperCase(Locale.ROOT);
        boolean active = request.active() == null || request.active();

        List<CouponCode> coupons = new ArrayList<>();
        for (int i = 0; i < request.count(); i++) {
            String code = generateUniqueCode(prefix);
            CouponCode coupon = CouponCode.builder()
                    .promotion(promotion)
                    .code(code)
                    .active(active)
                    .maxUses(request.maxUses())
                    .maxUsesPerCustomer(request.maxUsesPerCustomer())
                    .reservationTtlSeconds(request.reservationTtlSeconds())
                    .startsAt(request.startsAt())
                    .endsAt(request.endsAt())
                    .createdByUserSub(trimToNull(actorUserSub))
                    .updatedByUserSub(trimToNull(actorUserSub))
                    .build();
            coupons.add(coupon);
        }

        List<CouponCode> saved = couponCodeRepository.saveAll(coupons);
        List<CouponCodeResponse> responses = saved.stream().map(this::toResponse).toList();
        return new BatchCreateCouponsResponse(request.count(), saved.size(), responses);
    }

    private String generateUniqueCode(String prefix) {
        int maxRetries = 3;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            String code = prefix + "-" + randomAlphanumeric(8);
            if (!couponCodeRepository.existsByCodeIgnoreCase(code)) {
                return code;
            }
        }
        throw new ValidationException("Failed to generate unique coupon code after retries");
    }

    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private String randomAlphanumeric(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }

    @CacheEvict(cacheNames = "promotionAdminList", allEntries = true)
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public CouponCodeResponse deactivate(UUID promotionId, UUID couponId, String actorUserSub) {
        CouponCode coupon = requireCoupon(promotionId, couponId);
        coupon.setActive(false);
        coupon.setUpdatedByUserSub(trimToNull(actorUserSub));
        return toResponse(couponCodeRepository.save(coupon));
    }

    @CacheEvict(cacheNames = "promotionAdminList", allEntries = true)
    @Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
    public CouponCodeResponse activate(UUID promotionId, UUID couponId, String actorUserSub) {
        CouponCode coupon = requireCoupon(promotionId, couponId);
        coupon.setActive(true);
        coupon.setUpdatedByUserSub(trimToNull(actorUserSub));
        return toResponse(couponCodeRepository.save(coupon));
    }

    private CouponCode requireCoupon(UUID promotionId, UUID couponId) {
        return couponCodeRepository.findByIdAndPromotion_Id(couponId, promotionId)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found: " + couponId + " in promotion: " + promotionId));
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
