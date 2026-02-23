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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionQuoteServiceTest {

    @Mock
    private PromotionCampaignRepository promotionCampaignRepository;

    @Mock
    private CouponValidationService couponValidationService;

    private PromotionQuoteService service;

    private final Instant pricingAt = Instant.parse("2026-02-23T10:00:00Z");
    private final UUID vendorId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID productId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID categoryId = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @BeforeEach
    void setUp() {
        service = new PromotionQuoteService(promotionCampaignRepository, couponValidationService);
    }

    @Test
    void quote_exclusivePromotionOverridesPriorityAndSkipsOthers() {
        PromotionCampaign exclusive = basePromotion(
                "Exclusive 10%",
                PromotionApplicationLevel.CART,
                PromotionScopeType.ORDER,
                PromotionBenefitType.PERCENTAGE_OFF,
                "10.00",
                100,
                false,
                true
        );
        PromotionCampaign higherPriorityRegular = basePromotion(
                "Regular 30%",
                PromotionApplicationLevel.CART,
                PromotionScopeType.ORDER,
                PromotionBenefitType.PERCENTAGE_OFF,
                "30.00",
                1,
                true,
                false
        );
        when(promotionCampaignRepository.findAll()).thenReturn(List.of(higherPriorityRegular, exclusive));

        PromotionQuoteResponse quote = service.quote(singleLineOrderRequest("100.00", 1, "0.00"));

        assertEquals(new BigDecimal("100.00"), quote.subtotal());
        assertEquals(new BigDecimal("10.00"), quote.cartDiscountTotal());
        assertEquals(new BigDecimal("10.00"), quote.totalDiscount());
        assertEquals(new BigDecimal("90.00"), quote.grandTotal());
        assertEquals(1, quote.appliedPromotions().size());
        assertEquals(exclusive.getId(), quote.appliedPromotions().getFirst().promotionId());
        assertEquals(1, quote.rejectedPromotions().size());
        assertEquals(higherPriorityRegular.getId(), quote.rejectedPromotions().getFirst().promotionId());
        assertTrue(quote.rejectedPromotions().getFirst().reason().contains("exclusive"));
        verifyNoInteractions(couponValidationService);
    }

    @Test
    void quote_stackableLineAndCartPromotionsCanStack() {
        PromotionCampaign linePromo = basePromotion(
                "Product 10%",
                PromotionApplicationLevel.LINE_ITEM,
                PromotionScopeType.PRODUCT,
                PromotionBenefitType.PERCENTAGE_OFF,
                "10.00",
                1,
                true,
                false
        );
        linePromo.setTargetProductIds(Set.of(productId));

        PromotionCampaign cartPromo = basePromotion(
                "Cart 10%",
                PromotionApplicationLevel.CART,
                PromotionScopeType.ORDER,
                PromotionBenefitType.PERCENTAGE_OFF,
                "10.00",
                2,
                true,
                false
        );

        when(promotionCampaignRepository.findAll()).thenReturn(List.of(cartPromo, linePromo));

        PromotionQuoteResponse quote = service.quote(singleLineOrderRequest("100.00", 1, "0.00"));

        assertEquals(new BigDecimal("10.00"), quote.lineDiscountTotal());
        assertEquals(new BigDecimal("9.00"), quote.cartDiscountTotal());
        assertEquals(new BigDecimal("19.00"), quote.totalDiscount());
        assertEquals(new BigDecimal("81.00"), quote.grandTotal());
        assertEquals(2, quote.appliedPromotions().size());
        assertTrue(quote.rejectedPromotions().isEmpty());
    }

    @Test
    void quote_nonStackablePromotionBlocksSubsequentPromotions() {
        PromotionCampaign nonStackable = basePromotion(
                "Non-stackable 10%",
                PromotionApplicationLevel.CART,
                PromotionScopeType.ORDER,
                PromotionBenefitType.PERCENTAGE_OFF,
                "10.00",
                1,
                false,
                false
        );
        PromotionCampaign laterPromo = basePromotion(
                "Later 5%",
                PromotionApplicationLevel.CART,
                PromotionScopeType.ORDER,
                PromotionBenefitType.PERCENTAGE_OFF,
                "5.00",
                2,
                true,
                false
        );
        when(promotionCampaignRepository.findAll()).thenReturn(List.of(nonStackable, laterPromo));

        PromotionQuoteResponse quote = service.quote(singleLineOrderRequest("100.00", 1, "0.00"));

        assertEquals(1, quote.appliedPromotions().size());
        assertEquals(nonStackable.getId(), quote.appliedPromotions().getFirst().promotionId());
        assertEquals(1, quote.rejectedPromotions().size());
        assertEquals(laterPromo.getId(), quote.rejectedPromotions().getFirst().promotionId());
        assertTrue(quote.rejectedPromotions().getFirst().reason().contains("non-stackable"));
        assertEquals(new BigDecimal("10.00"), quote.totalDiscount());
        assertEquals(new BigDecimal("90.00"), quote.grandTotal());
    }

    @Test
    void quote_nonStackablePromotionCannotApplyAfterAnotherPromotionAlreadyApplied() {
        PromotionCampaign linePromo = basePromotion(
                "Line 10%",
                PromotionApplicationLevel.LINE_ITEM,
                PromotionScopeType.PRODUCT,
                PromotionBenefitType.PERCENTAGE_OFF,
                "10.00",
                1,
                true,
                false
        );
        linePromo.setTargetProductIds(Set.of(productId));

        PromotionCampaign nonStackableCart = basePromotion(
                "Non-stackable cart 5%",
                PromotionApplicationLevel.CART,
                PromotionScopeType.ORDER,
                PromotionBenefitType.PERCENTAGE_OFF,
                "5.00",
                2,
                false,
                false
        );

        when(promotionCampaignRepository.findAll()).thenReturn(List.of(linePromo, nonStackableCart));

        PromotionQuoteResponse quote = service.quote(singleLineOrderRequest("100.00", 1, "0.00"));

        assertEquals(1, quote.appliedPromotions().size());
        assertEquals(linePromo.getId(), quote.appliedPromotions().getFirst().promotionId());
        assertEquals(1, quote.rejectedPromotions().size());
        assertEquals(nonStackableCart.getId(), quote.rejectedPromotions().getFirst().promotionId());
        assertTrue(quote.rejectedPromotions().getFirst().reason().contains("not stackable"));
        assertEquals(new BigDecimal("10.00"), quote.lineDiscountTotal());
        assertEquals(new BigDecimal("0.00"), quote.cartDiscountTotal());
        assertEquals(new BigDecimal("90.00"), quote.grandTotal());
    }

    private PromotionQuoteRequest singleLineOrderRequest(String unitPrice, int quantity, String shippingAmount) {
        return new PromotionQuoteRequest(
                List.of(new PromotionQuoteLineRequest(
                        productId,
                        vendorId,
                        Set.of(categoryId),
                        new BigDecimal(unitPrice),
                        quantity
                )),
                new BigDecimal(shippingAmount),
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                null,
                "US",
                pricingAt
        );
    }

    private PromotionCampaign basePromotion(
            String name,
            PromotionApplicationLevel applicationLevel,
            PromotionScopeType scopeType,
            PromotionBenefitType benefitType,
            String benefitValue,
            int priority,
            boolean stackable,
            boolean exclusive
    ) {
        PromotionCampaign promotion = new PromotionCampaign();
        promotion.setId(UUID.randomUUID());
        promotion.setName(name);
        promotion.setDescription(name + " description");
        promotion.setVendorId(scopeType == PromotionScopeType.VENDOR ? vendorId : null);
        promotion.setScopeType(scopeType);
        promotion.setApplicationLevel(applicationLevel);
        promotion.setBenefitType(benefitType);
        promotion.setBenefitValue(new BigDecimal(benefitValue));
        promotion.setMinimumOrderAmount(null);
        promotion.setMaximumDiscountAmount(null);
        promotion.setFundingSource(PromotionFundingSource.PLATFORM);
        promotion.setStackable(stackable);
        promotion.setExclusive(exclusive);
        promotion.setAutoApply(true);
        promotion.setPriority(priority);
        promotion.setTargetProductIds(Set.of());
        promotion.setTargetCategoryIds(Set.of());
        promotion.setLifecycleStatus(PromotionLifecycleStatus.ACTIVE);
        promotion.setApprovalStatus(PromotionApprovalStatus.APPROVED);
        promotion.setStartsAt(pricingAt.minusSeconds(3600));
        promotion.setEndsAt(pricingAt.plusSeconds(3600));
        promotion.setCreatedAt(pricingAt.minusSeconds(priority));
        return promotion;
    }
}
