package com.rumal.promotion_service.service;

import com.rumal.promotion_service.dto.PromotionQuoteLineRequest;
import com.rumal.promotion_service.dto.PromotionQuoteRequest;
import com.rumal.promotion_service.dto.PromotionQuoteResponse;
import com.rumal.promotion_service.entity.PromotionApplicationLevel;
import com.rumal.promotion_service.entity.PromotionApprovalStatus;
import com.rumal.promotion_service.entity.PromotionBenefitType;
import com.rumal.promotion_service.entity.PromotionCampaign;
import com.rumal.promotion_service.entity.PromotionFundingSource;
import com.rumal.promotion_service.entity.PromotionLifecycleStatus;
import com.rumal.promotion_service.entity.PromotionScopeType;
import com.rumal.promotion_service.repo.PromotionCampaignRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionQuoteServiceCustomerSegmentTest {

    @Mock
    private PromotionCampaignRepository promotionCampaignRepository;

    @Mock
    private CouponValidationService couponValidationService;

    @Mock
    private CustomerPromotionEligibilityService customerPromotionEligibilityService;

    private PromotionQuoteService service;

    private final Instant pricingAt = Instant.parse("2026-03-08T06:00:00Z");
    private final UUID customerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID vendorId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID productId = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @BeforeEach
    void setUp() {
        service = new PromotionQuoteService(
                promotionCampaignRepository,
                couponValidationService,
                customerPromotionEligibilityService
        );
    }

    @Test
    void quote_doesNotTrustCallerSuppliedNewUserSegment() {
        PromotionCampaign promotion = newPromotion("New User 10%");
        promotion.setTargetSegments("NEW_USER");
        stubPromotions(promotion);
        when(customerPromotionEligibilityService.resolveProfile(eq(customerId), any()))
                .thenReturn(new CustomerPromotionEligibilityService.CustomerPromotionEligibilityProfile(
                        customerId,
                        pricingAt.minusSeconds(86400L * 365),
                        "GOLD",
                        4,
                        false,
                        Set.of("EXISTING_CUSTOMER", "LOYALTY_GOLD")
                ));

        PromotionQuoteResponse response = service.quote(new PromotionQuoteRequest(
                List.of(new PromotionQuoteLineRequest(productId, vendorId, Set.of(), new BigDecimal("100.00"), 1)),
                BigDecimal.ZERO,
                customerId,
                "NEW_USER",
                null,
                "US",
                pricingAt
        ));

        assertEquals(new BigDecimal("0.00"), response.totalDiscount());
        assertEquals(0, response.appliedPromotions().size());
        assertEquals(1, response.rejectedPromotions().size());
        assertTrue(response.rejectedPromotions().getFirst().reason().contains("new customers"));
        verify(customerPromotionEligibilityService).resolveProfile(eq(customerId), any());
        verify(couponValidationService, never()).findEligibleCouponForQuote(any(), any(), any());
    }

    @Test
    void quote_appliesDerivedNewUserPromotion() {
        PromotionCampaign promotion = newPromotion("New User 10%");
        promotion.setTargetSegments("NEW_USER");
        stubPromotions(promotion);
        when(customerPromotionEligibilityService.resolveProfile(eq(customerId), any()))
                .thenReturn(new CustomerPromotionEligibilityService.CustomerPromotionEligibilityProfile(
                        customerId,
                        pricingAt.minusSeconds(86400L * 7),
                        null,
                        0,
                        true,
                        Set.of("NEW_USER")
                ));

        PromotionQuoteResponse response = service.quote(new PromotionQuoteRequest(
                List.of(new PromotionQuoteLineRequest(productId, vendorId, Set.of(), new BigDecimal("100.00"), 1)),
                BigDecimal.ZERO,
                customerId,
                null,
                null,
                "US",
                pricingAt
        ));

        assertEquals(1, response.appliedPromotions().size());
        assertEquals(new BigDecimal("10.00"), response.totalDiscount());
        assertEquals(new BigDecimal("90.00"), response.grandTotal());
        assertTrue(response.rejectedPromotions().isEmpty());
        verify(customerPromotionEligibilityService).resolveProfile(eq(customerId), any());
    }

    private void stubPromotions(PromotionCampaign promotion) {
        when(promotionCampaignRepository.findActiveByScope(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(promotion)));
    }

    private PromotionCampaign newPromotion(String name) {
        Instant now = Instant.now();
        PromotionCampaign promotion = new PromotionCampaign();
        promotion.setId(UUID.randomUUID());
        promotion.setName(name);
        promotion.setDescription(name + " description");
        promotion.setScopeType(PromotionScopeType.ORDER);
        promotion.setApplicationLevel(PromotionApplicationLevel.CART);
        promotion.setBenefitType(PromotionBenefitType.PERCENTAGE_OFF);
        promotion.setBenefitValue(new BigDecimal("10.00"));
        promotion.setFundingSource(PromotionFundingSource.PLATFORM);
        promotion.setStackable(true);
        promotion.setExclusive(false);
        promotion.setAutoApply(true);
        promotion.setPriority(1);
        promotion.setLifecycleStatus(PromotionLifecycleStatus.ACTIVE);
        promotion.setApprovalStatus(PromotionApprovalStatus.APPROVED);
        promotion.setStartsAt(now.minusSeconds(3600));
        promotion.setEndsAt(now.plusSeconds(3600));
        promotion.setCreatedAt(now.minusSeconds(60));
        promotion.setTargetProductIds(Set.of());
        promotion.setTargetCategoryIds(Set.of());
        return promotion;
    }
}
