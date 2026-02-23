package com.rumal.promotion_service.service;

import com.rumal.promotion_service.dto.PromotionAnalyticsResponse;
import com.rumal.promotion_service.entity.CouponCode;
import com.rumal.promotion_service.entity.CouponReservation;
import com.rumal.promotion_service.entity.CouponReservationStatus;
import com.rumal.promotion_service.entity.PromotionApplicationLevel;
import com.rumal.promotion_service.entity.PromotionApprovalStatus;
import com.rumal.promotion_service.entity.PromotionBenefitType;
import com.rumal.promotion_service.entity.PromotionCampaign;
import com.rumal.promotion_service.entity.PromotionFundingSource;
import com.rumal.promotion_service.entity.PromotionLifecycleStatus;
import com.rumal.promotion_service.entity.PromotionScopeType;
import com.rumal.promotion_service.repo.CouponCodeRepository;
import com.rumal.promotion_service.repo.CouponReservationRepository;
import com.rumal.promotion_service.repo.PromotionCampaignRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PromotionAnalyticsServiceIntegrationTest {

    @MockitoBean
    private CacheManager cacheManager;

    @Autowired
    private PromotionCampaignRepository promotionCampaignRepository;

    @Autowired
    private CouponCodeRepository couponCodeRepository;

    @Autowired
    private CouponReservationRepository couponReservationRepository;

    @Autowired
    private TestEntityManager entityManager;

    private PromotionAnalyticsService promotionAnalyticsService;

    @BeforeEach
    void setUp() {
        promotionAnalyticsService = new PromotionAnalyticsService(
                promotionCampaignRepository,
                couponCodeRepository,
                couponReservationRepository
        );
    }

    @Test
    void get_returnsCouponAndBudgetAnalytics() {
        PromotionCampaign promotion = persistPromotion("Analytics Promo", "100.00", "30.00");
        CouponCode activeCoupon = persistCoupon(promotion, "SAVE10", true);
        persistCoupon(promotion, "SAVE11", false);

        persistReservation(activeCoupon, promotion.getId(), CouponReservationStatus.RESERVED, "10.00", 300);
        persistReservation(activeCoupon, promotion.getId(), CouponReservationStatus.RESERVED, "7.00", -10);
        persistReservation(activeCoupon, promotion.getId(), CouponReservationStatus.COMMITTED, "20.00", 300);
        persistReservation(activeCoupon, promotion.getId(), CouponReservationStatus.RELEASED, "5.00", 300);
        persistReservation(activeCoupon, promotion.getId(), CouponReservationStatus.EXPIRED, "3.00", -300);

        entityManager.flush();
        entityManager.clear();

        PromotionAnalyticsResponse response = promotionAnalyticsService.get(promotion.getId());

        assertEquals(promotion.getId(), response.promotionId());
        assertEquals(2L, response.couponCodeCount());
        assertEquals(1L, response.activeCouponCodeCount());
        assertEquals(5L, response.reservationCount());
        assertEquals(1L, response.activeReservedReservationCount());
        assertEquals(1L, response.committedReservationCount());
        assertEquals(1L, response.releasedReservationCount());
        assertEquals(1L, response.expiredReservationCount());
        assertEquals(new BigDecimal("100.00"), response.budgetAmount());
        assertEquals(new BigDecimal("30.00"), response.burnedBudgetAmount());
        assertEquals(new BigDecimal("10.00"), response.activeReservedBudgetAmount());
        assertEquals(new BigDecimal("60.00"), response.remainingBudgetAmount());
        assertEquals(new BigDecimal("20.00"), response.committedDiscountAmount());
        assertEquals(new BigDecimal("5.00"), response.releasedDiscountAmount());
    }

    private PromotionCampaign persistPromotion(String name, String budgetAmount, String burnedBudgetAmount) {
        PromotionCampaign promotion = new PromotionCampaign();
        promotion.setName(name);
        promotion.setDescription(name + " description");
        promotion.setVendorId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        promotion.setScopeType(PromotionScopeType.ORDER);
        promotion.setApplicationLevel(PromotionApplicationLevel.CART);
        promotion.setBenefitType(PromotionBenefitType.FIXED_AMOUNT_OFF);
        promotion.setBenefitValue(new BigDecimal("10.00"));
        promotion.setMinimumOrderAmount(null);
        promotion.setMaximumDiscountAmount(null);
        promotion.setBudgetAmount(new BigDecimal(budgetAmount));
        promotion.setBurnedBudgetAmount(new BigDecimal(burnedBudgetAmount));
        promotion.setFundingSource(PromotionFundingSource.PLATFORM);
        promotion.setStackable(true);
        promotion.setExclusive(false);
        promotion.setAutoApply(true);
        promotion.setPriority(100);
        promotion.setLifecycleStatus(PromotionLifecycleStatus.ACTIVE);
        promotion.setApprovalStatus(PromotionApprovalStatus.APPROVED);
        promotion.setStartsAt(Instant.now().minusSeconds(3600));
        promotion.setEndsAt(Instant.now().plusSeconds(3600));
        entityManager.persist(promotion);
        return promotion;
    }

    private CouponCode persistCoupon(PromotionCampaign promotion, String code, boolean active) {
        CouponCode couponCode = new CouponCode();
        couponCode.setPromotion(promotion);
        couponCode.setCode(code);
        couponCode.setActive(active);
        couponCode.setStartsAt(Instant.now().minusSeconds(3600));
        couponCode.setEndsAt(Instant.now().plusSeconds(3600));
        entityManager.persist(couponCode);
        return couponCode;
    }

    private void persistReservation(
            CouponCode couponCode,
            UUID promotionId,
            CouponReservationStatus status,
            String discountAmount,
            int expiresInSeconds
    ) {
        Instant now = Instant.now();
        CouponReservation reservation = CouponReservation.builder()
                .couponCode(couponCode)
                .promotionId(promotionId)
                .customerId(UUID.randomUUID())
                .couponCodeText(couponCode.getCode())
                .requestKey(UUID.randomUUID().toString())
                .status(status)
                .reservedDiscountAmount(new BigDecimal(discountAmount))
                .quotedSubtotal(new BigDecimal("100.00"))
                .quotedGrandTotal(new BigDecimal("90.00"))
                .reservedAt(now.minusSeconds(30))
                .expiresAt(now.plusSeconds(expiresInSeconds))
                .committedAt(status == CouponReservationStatus.COMMITTED ? now.minusSeconds(20) : null)
                .releasedAt(status == CouponReservationStatus.RELEASED ? now.minusSeconds(10) : null)
                .releaseReason(status == CouponReservationStatus.RELEASED ? "test_release" : null)
                .build();
        entityManager.persist(reservation);
    }
}
