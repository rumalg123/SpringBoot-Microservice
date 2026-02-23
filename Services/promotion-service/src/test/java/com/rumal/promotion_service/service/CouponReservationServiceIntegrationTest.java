package com.rumal.promotion_service.service;

import com.rumal.promotion_service.dto.AppliedPromotionQuoteEntry;
import com.rumal.promotion_service.dto.CommitCouponReservationRequest;
import com.rumal.promotion_service.dto.CouponReservationResponse;
import com.rumal.promotion_service.dto.CreateCouponReservationRequest;
import com.rumal.promotion_service.dto.PromotionQuoteLineRequest;
import com.rumal.promotion_service.dto.PromotionQuoteRequest;
import com.rumal.promotion_service.dto.PromotionQuoteResponse;
import com.rumal.promotion_service.dto.ReleaseCouponReservationRequest;
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
import com.rumal.promotion_service.exception.ValidationException;
import com.rumal.promotion_service.repo.CouponReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.cache.CacheManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class CouponReservationServiceIntegrationTest {

    @MockitoBean
    private CacheManager cacheManager;

    @Autowired
    private CouponReservationRepository couponReservationRepository;

    @Autowired
    private TestEntityManager entityManager;

    private CouponValidationService couponValidationService;
    private PromotionQuoteService promotionQuoteService;
    private CouponReservationService couponReservationService;

    @BeforeEach
    void setUp() {
        couponValidationService = Mockito.mock(CouponValidationService.class);
        promotionQuoteService = Mockito.mock(PromotionQuoteService.class);
        couponReservationService = new CouponReservationService(
                couponValidationService,
                couponReservationRepository,
                promotionQuoteService
        );
        ReflectionTestUtils.setField(couponReservationService, "defaultReservationTtlSeconds", 900);
        ReflectionTestUtils.setField(couponReservationService, "maxReservationTtlSeconds", 1800);
    }

    @Test
    void reserve_persistsReservationAndReturnsQuotePayload() {
        PromotionCampaign promotion = persistPromotion("Coupon Promotion");
        CouponCode couponCode = persistCoupon(promotion, "SAVE10", 600);
        UUID customerId = UUID.randomUUID();
        CreateCouponReservationRequest request = reservationRequest("SAVE10", customerId, "reserve-1");
        PromotionQuoteResponse quote = quoteResponseForCoupon(promotion.getId(), "SAVE10", "100.00", "12.50", "87.50");

        when(promotionQuoteService.quote(request.quoteRequest())).thenReturn(quote);
        when(couponValidationService.findEligibleCouponForReservation(eq("SAVE10"), eq(customerId), any(Instant.class)))
                .thenReturn(Optional.of(CouponValidationService.CouponEligibility.eligible(couponCode)));

        CouponReservationResponse response = couponReservationService.reserve(request);

        assertNotNull(response.id());
        assertEquals("RESERVED", response.status());
        assertEquals(couponCode.getId(), response.couponCodeId());
        assertEquals(promotion.getId(), response.promotionId());
        assertEquals(new BigDecimal("12.50"), response.reservedDiscountAmount());
        assertEquals(new BigDecimal("100.00"), response.quotedSubtotal());
        assertEquals(new BigDecimal("87.50"), response.quotedGrandTotal());
        assertNotNull(response.reservedAt());
        assertNotNull(response.expiresAt());
        assertTrue(response.expiresAt().isAfter(response.reservedAt()));
        assertNotNull(response.quote());

        CouponReservation stored = couponReservationRepository.findById(response.id()).orElseThrow();
        assertEquals(CouponReservationStatus.RESERVED, stored.getStatus());
        assertEquals("SAVE10", stored.getCouponCodeText());
        assertEquals(request.requestKey(), stored.getRequestKey());
        assertEquals(new BigDecimal("12.50"), stored.getReservedDiscountAmount());
    }

    @Test
    void reserve_withSameRequestKeyReturnsExistingReservationIdempotently() {
        PromotionCampaign promotion = persistPromotion("Coupon Promotion");
        CouponCode couponCode = persistCoupon(promotion, "SAVE10", 600);
        UUID customerId = UUID.randomUUID();
        CreateCouponReservationRequest request = reservationRequest("SAVE10", customerId, "idem-key-1");
        PromotionQuoteResponse quote = quoteResponseForCoupon(promotion.getId(), "SAVE10", "100.00", "10.00", "90.00");

        when(promotionQuoteService.quote(request.quoteRequest())).thenReturn(quote);
        when(couponValidationService.findEligibleCouponForReservation(eq("SAVE10"), eq(customerId), any(Instant.class)))
                .thenReturn(Optional.of(CouponValidationService.CouponEligibility.eligible(couponCode)));

        CouponReservationResponse first = couponReservationService.reserve(request);
        CouponReservationResponse second = couponReservationService.reserve(request);

        assertEquals(first.id(), second.id());
        assertEquals("RESERVED", second.status());
        assertEquals(1L, couponReservationRepository.count());
        verify(promotionQuoteService, times(1)).quote(request.quoteRequest());
        verify(couponValidationService, times(1))
                .findEligibleCouponForReservation(eq("SAVE10"), eq(customerId), any(Instant.class));
    }

    @Test
    void commit_thenReleaseLifecycleTransitionsPersistCorrectly() {
        PromotionCampaign promotion = persistPromotion("Coupon Promotion");
        CouponCode couponCode = persistCoupon(promotion, "SAVE10", 600);
        UUID customerId = UUID.randomUUID();
        CouponReservation reservation = persistReservation(couponCode, promotion.getId(), customerId, CouponReservationStatus.RESERVED, 300);
        UUID orderId = UUID.randomUUID();

        CouponReservationResponse committed = couponReservationService.commit(reservation.getId(), new CommitCouponReservationRequest(orderId));
        assertEquals("COMMITTED", committed.status());
        assertEquals(orderId, committed.orderId());
        assertNotNull(committed.committedAt());

        CouponReservationResponse committedAgain = couponReservationService.commit(reservation.getId(), new CommitCouponReservationRequest(orderId));
        assertEquals("COMMITTED", committedAgain.status());
        assertEquals(orderId, committedAgain.orderId());

        CouponReservationResponse released = couponReservationService.release(
                reservation.getId(),
                new ReleaseCouponReservationRequest("order_cancelled")
        );
        assertEquals("RELEASED", released.status());
        assertEquals(orderId, released.orderId());
        assertEquals("order_cancelled", released.releaseReason());
        assertNotNull(released.releasedAt());

        CouponReservation stored = couponReservationRepository.findById(reservation.getId()).orElseThrow();
        assertEquals(CouponReservationStatus.RELEASED, stored.getStatus());
        assertEquals(orderId, stored.getOrderId());
    }

    @Test
    void release_reservedReservationMarksReleasedAndIsIdempotent() {
        PromotionCampaign promotion = persistPromotion("Coupon Promotion");
        CouponCode couponCode = persistCoupon(promotion, "SAVE10", 600);
        UUID customerId = UUID.randomUUID();
        CouponReservation reservation = persistReservation(couponCode, promotion.getId(), customerId, CouponReservationStatus.RESERVED, 300);

        CouponReservationResponse released = couponReservationService.release(
                reservation.getId(),
                new ReleaseCouponReservationRequest("checkout_failed")
        );

        assertEquals("RELEASED", released.status());
        assertNotNull(released.releasedAt());
        assertEquals("checkout_failed", released.releaseReason());

        CouponReservationResponse releasedAgain = couponReservationService.release(
                reservation.getId(),
                new ReleaseCouponReservationRequest("ignored_repeat")
        );
        assertEquals("RELEASED", releasedAgain.status());
        assertEquals("checkout_failed", releasedAgain.releaseReason());

        CouponReservation stored = couponReservationRepository.findById(reservation.getId()).orElseThrow();
        assertEquals(CouponReservationStatus.RELEASED, stored.getStatus());
        assertEquals("checkout_failed", stored.getReleaseReason());
    }

    @Test
    void commit_orReleaseExpiredReservationHandlesExpiryState() {
        PromotionCampaign promotion = persistPromotion("Coupon Promotion");
        CouponCode couponCode = persistCoupon(promotion, "SAVE10", 600);
        UUID customerId = UUID.randomUUID();
        CouponReservation reservation = persistReservation(couponCode, promotion.getId(), customerId, CouponReservationStatus.RESERVED, -5);

        ValidationException commitError = assertThrows(
                ValidationException.class,
                () -> couponReservationService.commit(reservation.getId(), new CommitCouponReservationRequest(UUID.randomUUID()))
        );
        assertTrue(commitError.getMessage().contains("expired"));

        CouponReservationResponse expiredRelease = couponReservationService.release(
                reservation.getId(),
                new ReleaseCouponReservationRequest("checkout_timeout")
        );
        assertEquals("EXPIRED", expiredRelease.status());
        assertNull(expiredRelease.releasedAt());

        CouponReservation stored = couponReservationRepository.findById(reservation.getId()).orElseThrow();
        assertEquals(CouponReservationStatus.EXPIRED, stored.getStatus());
    }

    private PromotionCampaign persistPromotion(String name) {
        PromotionCampaign promotion = new PromotionCampaign();
        promotion.setName(name);
        promotion.setDescription(name + " description");
        promotion.setVendorId(null);
        promotion.setScopeType(PromotionScopeType.ORDER);
        promotion.setApplicationLevel(PromotionApplicationLevel.CART);
        promotion.setBenefitType(PromotionBenefitType.PERCENTAGE_OFF);
        promotion.setBenefitValue(new BigDecimal("10.00"));
        promotion.setMinimumOrderAmount(null);
        promotion.setMaximumDiscountAmount(null);
        promotion.setFundingSource(PromotionFundingSource.PLATFORM);
        promotion.setStackable(false);
        promotion.setExclusive(false);
        promotion.setAutoApply(false);
        promotion.setPriority(10);
        promotion.setTargetProductIds(Set.of());
        promotion.setTargetCategoryIds(Set.of());
        promotion.setLifecycleStatus(PromotionLifecycleStatus.ACTIVE);
        promotion.setApprovalStatus(PromotionApprovalStatus.APPROVED);
        promotion.setStartsAt(Instant.now().minusSeconds(3600));
        promotion.setEndsAt(Instant.now().plusSeconds(3600));
        entityManager.persistAndFlush(promotion);
        return promotion;
    }

    private CouponCode persistCoupon(PromotionCampaign promotion, String code, Integer ttlSeconds) {
        CouponCode couponCode = new CouponCode();
        couponCode.setPromotion(promotion);
        couponCode.setCode(code);
        couponCode.setActive(true);
        couponCode.setMaxUses(null);
        couponCode.setMaxUsesPerCustomer(null);
        couponCode.setReservationTtlSeconds(ttlSeconds);
        couponCode.setStartsAt(Instant.now().minusSeconds(3600));
        couponCode.setEndsAt(Instant.now().plusSeconds(3600));
        entityManager.persistAndFlush(couponCode);
        return couponCode;
    }

    private CouponReservation persistReservation(
            CouponCode couponCode,
            UUID promotionId,
            UUID customerId,
            CouponReservationStatus status,
            int expiresInSeconds
    ) {
        Instant now = Instant.now();
        CouponReservation reservation = CouponReservation.builder()
                .couponCode(couponCode)
                .promotionId(promotionId)
                .customerId(customerId)
                .couponCodeText(couponCode.getCode())
                .requestKey(UUID.randomUUID().toString())
                .status(status)
                .reservedDiscountAmount(new BigDecimal("10.00"))
                .quotedSubtotal(new BigDecimal("100.00"))
                .quotedGrandTotal(new BigDecimal("90.00"))
                .reservedAt(now.minusSeconds(60))
                .expiresAt(now.plusSeconds(expiresInSeconds))
                .build();
        entityManager.persistAndFlush(reservation);
        entityManager.clear();
        return reservation;
    }

    private CreateCouponReservationRequest reservationRequest(String couponCode, UUID customerId, String requestKey) {
        PromotionQuoteRequest quoteRequest = new PromotionQuoteRequest(
                List.of(new PromotionQuoteLineRequest(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        Set.of(UUID.randomUUID()),
                        new BigDecimal("100.00"),
                        1
                )),
                new BigDecimal("0.00"),
                customerId,
                couponCode,
                "US",
                Instant.parse("2026-02-23T10:00:00Z")
        );
        return new CreateCouponReservationRequest(couponCode, customerId, quoteRequest, requestKey);
    }

    private PromotionQuoteResponse quoteResponseForCoupon(
            UUID promotionId,
            String couponName,
            String subtotal,
            String totalDiscount,
            String grandTotal
    ) {
        return new PromotionQuoteResponse(
                new BigDecimal(subtotal),
                new BigDecimal("0.00"),
                new BigDecimal(totalDiscount),
                new BigDecimal("0.00"),
                new BigDecimal("0.00"),
                new BigDecimal(totalDiscount),
                new BigDecimal(grandTotal),
                List.of(),
                List.of(new AppliedPromotionQuoteEntry(
                        promotionId,
                        couponName,
                        PromotionApplicationLevel.CART,
                        PromotionBenefitType.PERCENTAGE_OFF,
                        10,
                        false,
                        new BigDecimal(totalDiscount)
                )),
                List.of(),
                Instant.parse("2026-02-23T10:00:00Z")
        );
    }

}
