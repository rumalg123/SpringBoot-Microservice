package com.rumal.promotion_service.service;

import com.rumal.promotion_service.dto.AppliedPromotionQuoteEntry;
import com.rumal.promotion_service.dto.PromotionQuoteLineRequest;
import com.rumal.promotion_service.dto.PromotionQuoteLineResponse;
import com.rumal.promotion_service.dto.PromotionQuoteRequest;
import com.rumal.promotion_service.dto.PromotionQuoteResponse;
import com.rumal.promotion_service.dto.RejectedPromotionQuoteEntry;
import com.rumal.promotion_service.entity.PromotionApplicationLevel;
import com.rumal.promotion_service.entity.PromotionApprovalStatus;
import com.rumal.promotion_service.entity.PromotionBenefitType;
import com.rumal.promotion_service.entity.PromotionCampaign;
import com.rumal.promotion_service.entity.PromotionLifecycleStatus;
import com.rumal.promotion_service.entity.PromotionScopeType;
import com.rumal.promotion_service.entity.PromotionSpendTier;
import com.rumal.promotion_service.exception.ValidationException;
import com.rumal.promotion_service.repo.PromotionCampaignRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PromotionQuoteService {

    private final PromotionCampaignRepository promotionCampaignRepository;
    private final CouponValidationService couponValidationService;

    @Transactional(readOnly = true)
    public PromotionQuoteResponse quote(PromotionQuoteRequest request) {
        if (request == null || request.lines() == null || request.lines().isEmpty()) {
            throw new ValidationException("At least one line is required");
        }

        Instant pricedAt = Instant.now();
        BigDecimal shippingAmount = normalizeMoney(request.shippingAmount() == null ? BigDecimal.ZERO : request.shippingAmount());

        List<LineState> lineStates = new ArrayList<>(request.lines().size());
        BigDecimal subtotal = BigDecimal.ZERO;
        for (PromotionQuoteLineRequest line : request.lines()) {
            BigDecimal unitPrice = normalizeMoney(line.unitPrice());
            BigDecimal lineSubtotal = normalizeMoney(unitPrice.multiply(BigDecimal.valueOf(line.quantity())));
            subtotal = subtotal.add(lineSubtotal);
            lineStates.add(new LineState(line, unitPrice, lineSubtotal));
        }
        subtotal = normalizeMoney(subtotal);

        List<AppliedPromotionQuoteEntry> applied = new ArrayList<>();
        List<RejectedPromotionQuoteEntry> rejected = new ArrayList<>();

        CouponValidationService.CouponEligibility couponEligibility = null;
        PromotionCampaign couponPromotion = null;
        if (request.couponCode() != null && !request.couponCode().isBlank()) {
            couponEligibility = couponValidationService.findEligibleCouponForQuote(request.couponCode(), request.customerId(), pricedAt)
                    .orElseGet(() -> CouponValidationService.CouponEligibility.ineligible(null, "Coupon code not found"));
            if (!couponEligibility.eligible()) {
                rejected.add(new RejectedPromotionQuoteEntry(
                        couponEligibility.promotionId(),
                        request.couponCode().trim(),
                        couponEligibility.reason()
                ));
            } else if (couponEligibility.couponCode() == null || couponEligibility.couponCode().getPromotion() == null) {
                rejected.add(new RejectedPromotionQuoteEntry(null, request.couponCode().trim(), "Coupon promotion link is invalid"));
            } else {
                couponPromotion = couponEligibility.couponCode().getPromotion();
            }
        }

        String customerSegment = request.customerSegment() == null ? null : request.customerSegment().trim();

        // Extract vendor IDs from cart lines for scope-based pre-filtering
        Set<UUID> cartVendorIds = request.lines().stream()
                .map(PromotionQuoteLineRequest::vendorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (cartVendorIds.isEmpty()) {
            cartVendorIds = Set.of(UUID.fromString("00000000-0000-0000-0000-000000000000"));
        }

        Map<UUID, PromotionCandidate> candidatesById = new LinkedHashMap<>();
        List<PromotionApprovalStatus> eligibleStatuses = List.of(PromotionApprovalStatus.NOT_REQUIRED, PromotionApprovalStatus.APPROVED);
        int pageNum = 0;
        Page<PromotionCampaign> promoPage;
        do {
            promoPage = promotionCampaignRepository.findActiveByScope(
                    PromotionLifecycleStatus.ACTIVE, eligibleStatuses, cartVendorIds, PageRequest.of(pageNum, 200));
            promoPage.getContent().stream()
                    .filter(p -> withinWindow(p, pricedAt))
                    .filter(p -> matchesSegment(p, customerSegment))
                    .filter(p -> withinFlashSaleWindow(p, pricedAt))
                    .forEach(p -> candidatesById.put(p.getId(), new PromotionCandidate(p, false, null)));
            pageNum++;
        } while (promoPage.hasNext());
        if (couponPromotion != null && couponPromotion.getId() != null) {
            candidatesById.put(couponPromotion.getId(), new PromotionCandidate(couponPromotion, true, request.couponCode().trim()));
        }

        List<PromotionCandidate> candidates = candidatesById.values().stream()
                .sorted(Comparator
                        .comparing((PromotionCandidate c) -> c.promotion().isExclusive()).reversed()
                        .thenComparing(c -> c.promotion().getPriority())
                        .thenComparing((PromotionCandidate c) -> !c.explicitCoupon())
                        .thenComparing(c -> c.promotion().getCreatedAt(), Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(c -> c.promotion().getId(), Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        boolean exclusiveApplied = false;
        boolean nonStackableApplied = false;
        int appliedCount = 0;
        Set<String> appliedStackingGroups = new HashSet<>();
        BigDecimal cartDiscountTotal = BigDecimal.ZERO;
        BigDecimal shippingDiscountTotal = BigDecimal.ZERO;

        // Determine the global max stack count (smallest maxStackCount from any candidate that defines it)
        Integer globalMaxStack = null;
        for (PromotionCandidate c : candidates) {
            Integer msc = c.promotion().getMaxStackCount();
            if (msc != null && msc > 0) {
                globalMaxStack = (globalMaxStack == null) ? msc : Math.min(globalMaxStack, msc);
            }
        }

        for (PromotionCandidate candidate : candidates) {
            PromotionCampaign promotion = candidate.promotion();
            if (!candidate.explicitCoupon() && !promotion.isAutoApply()) {
                rejected.add(new RejectedPromotionQuoteEntry(promotion.getId(), promotion.getName(), "Promotion requires explicit coupon/manual trigger"));
                continue;
            }
            if (exclusiveApplied) {
                rejected.add(new RejectedPromotionQuoteEntry(promotion.getId(), promotion.getName(), "Skipped because an exclusive promotion already applied"));
                continue;
            }
            if (nonStackableApplied) {
                rejected.add(new RejectedPromotionQuoteEntry(promotion.getId(), promotion.getName(), "Skipped because a non-stackable promotion already applied"));
                continue;
            }
            if (!promotion.isStackable() && !applied.isEmpty()) {
                rejected.add(new RejectedPromotionQuoteEntry(promotion.getId(), promotion.getName(), "Promotion is not stackable with previously applied promotions"));
                continue;
            }
            // Stacking group check: promotions in the same group cannot stack
            String stackingGroup = promotion.getStackingGroup();
            if (stackingGroup != null && !stackingGroup.isBlank()) {
                String normalizedGroup = stackingGroup.trim().toLowerCase(Locale.ROOT);
                if (appliedStackingGroups.contains(normalizedGroup)) {
                    rejected.add(new RejectedPromotionQuoteEntry(promotion.getId(), promotion.getName(), "Promotion conflicts with stacking group: " + stackingGroup.trim()));
                    continue;
                }
            }
            // Max stack count check
            if (globalMaxStack != null && appliedCount >= globalMaxStack) {
                rejected.add(new RejectedPromotionQuoteEntry(promotion.getId(), promotion.getName(), "Maximum number of stacked promotions reached (" + globalMaxStack + ")"));
                continue;
            }
            // Flash sale redemption limit check
            if (promotion.isFlashSale() && promotion.getFlashSaleMaxRedemptions() != null
                    && promotion.getFlashSaleRedemptionCount() >= promotion.getFlashSaleMaxRedemptions()) {
                rejected.add(new RejectedPromotionQuoteEntry(promotion.getId(), promotion.getName(), "Flash sale redemption limit reached"));
                continue;
            }

            BigDecimal budgetRemaining = remainingBudgetForQuote(promotion);
            if (budgetRemaining != null && budgetRemaining.compareTo(BigDecimal.ZERO) <= 0) {
                rejected.add(new RejectedPromotionQuoteEntry(promotion.getId(), promotion.getName(), "Campaign budget exhausted"));
                continue;
            }
            List<BigDecimal> lineDiscountSnapshot = budgetRemaining == null ? List.of() : snapshotLineDiscounts(lineStates);

            PromotionApplicationResult result = applyPromotion(promotion, lineStates, subtotal, shippingAmount, shippingDiscountTotal);
            if (!result.applied()) {
                rejected.add(new RejectedPromotionQuoteEntry(promotion.getId(), promotion.getName(), result.reason()));
                continue;
            }
            if (budgetRemaining != null && result.discountAmount().compareTo(budgetRemaining) > 0) {
                restoreLineDiscounts(lineStates, lineDiscountSnapshot);
                rejected.add(new RejectedPromotionQuoteEntry(promotion.getId(), promotion.getName(), "Campaign budget remaining is insufficient"));
                continue;
            }

            if (promotion.getApplicationLevel() == PromotionApplicationLevel.CART) {
                cartDiscountTotal = normalizeMoney(cartDiscountTotal.add(result.discountAmount()));
            } else if (promotion.getApplicationLevel() == PromotionApplicationLevel.SHIPPING) {
                shippingDiscountTotal = normalizeMoney(shippingDiscountTotal.add(result.discountAmount()));
            }

            applied.add(new AppliedPromotionQuoteEntry(
                    promotion.getId(),
                    promotion.getName(),
                    promotion.getApplicationLevel(),
                    promotion.getBenefitType(),
                    promotion.getPriority(),
                    promotion.isExclusive(),
                    result.discountAmount()
            ));

            appliedCount++;
            if (stackingGroup != null && !stackingGroup.isBlank()) {
                appliedStackingGroups.add(stackingGroup.trim().toLowerCase(Locale.ROOT));
            }
            if (promotion.isExclusive()) {
                exclusiveApplied = true;
            }
            if (!promotion.isStackable()) {
                nonStackableApplied = true;
            }
        }

        BigDecimal lineDiscountTotal = normalizeMoney(lineStates.stream()
                .map(LineState::lineDiscount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        shippingDiscountTotal = minMoney(shippingDiscountTotal, shippingAmount);
        cartDiscountTotal = minMoney(cartDiscountTotal, normalizeMoney(subtotal.subtract(lineDiscountTotal)));
        BigDecimal totalDiscount = normalizeMoney(lineDiscountTotal.add(cartDiscountTotal).add(shippingDiscountTotal));
        BigDecimal grandTotal = normalizeMoney(
                subtotal.subtract(lineDiscountTotal).subtract(cartDiscountTotal).add(shippingAmount).subtract(shippingDiscountTotal)
        );
        if (grandTotal.compareTo(BigDecimal.ZERO) < 0) {
            grandTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        List<PromotionQuoteLineResponse> lineResponses = lineStates.stream()
                .map(line -> new PromotionQuoteLineResponse(
                        line.request().productId(),
                        line.request().vendorId(),
                        Set.copyOf(line.request().categoryIdsOrEmpty()),
                        line.unitPrice(),
                        line.request().quantity(),
                        line.lineSubtotal(),
                        line.lineDiscount(),
                        line.lineTotal()
                ))
                .toList();

        return new PromotionQuoteResponse(
                subtotal,
                lineDiscountTotal,
                cartDiscountTotal,
                shippingAmount,
                shippingDiscountTotal,
                totalDiscount,
                grandTotal,
                lineResponses,
                List.copyOf(applied),
                List.copyOf(rejected),
                pricedAt
        );
    }

    private PromotionApplicationResult applyPromotion(
            PromotionCampaign promotion,
            List<LineState> lineStates,
            BigDecimal subtotal,
            BigDecimal shippingAmount,
            BigDecimal shippingDiscountAlreadyApplied
    ) {
        if (promotion.getMinimumOrderAmount() != null && subtotal.compareTo(promotion.getMinimumOrderAmount()) < 0) {
            return PromotionApplicationResult.rejected("Minimum order amount not met");
        }

        return switch (promotion.getApplicationLevel()) {
            case LINE_ITEM -> applyLineLevelPromotion(promotion, lineStates);
            case CART -> applyCartLevelPromotion(promotion, lineStates);
            case SHIPPING -> applyShippingPromotion(promotion, lineStates, shippingAmount, shippingDiscountAlreadyApplied);
        };
    }

    private PromotionApplicationResult applyLineLevelPromotion(PromotionCampaign promotion, List<LineState> lineStates) {
        List<LineState> eligibleLines = eligibleLinesForPromotion(promotion, lineStates);
        if (eligibleLines.isEmpty()) {
            return PromotionApplicationResult.rejected("No eligible line items");
        }
        if (promotion.getBenefitType() == PromotionBenefitType.BUY_X_GET_Y) {
            return applyBuyXGetYLinePromotion(promotion, eligibleLines);
        }
        if (promotion.getBenefitType() == PromotionBenefitType.FREE_SHIPPING) {
            return PromotionApplicationResult.rejected("FREE_SHIPPING promotions must use SHIPPING application level");
        }
        if (promotion.getBenefitType() == PromotionBenefitType.BUNDLE_DISCOUNT) {
            return PromotionApplicationResult.rejected("BUNDLE_DISCOUNT promotions must use CART application level");
        }

        List<BigDecimal> beforeDiscounts = eligibleLines.stream()
                .map(l -> normalizeMoney(l.lineDiscount()))
                .toList();

        BigDecimal totalDiscount = BigDecimal.ZERO;
        List<BigDecimal> appliedPerLine = new ArrayList<>(eligibleLines.size());
        for (LineState line : eligibleLines) {
            BigDecimal remaining = line.lineTotal();
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                appliedPerLine.add(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                continue;
            }
            BigDecimal discount = calculateDiscountForAmount(promotion, remaining);
            if (discount.compareTo(BigDecimal.ZERO) <= 0) {
                appliedPerLine.add(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                continue;
            }
            BigDecimal applied = line.applyLineDiscount(discount);
            appliedPerLine.add(applied);
            totalDiscount = totalDiscount.add(applied);
        }

        BigDecimal uncapped = normalizeMoney(totalDiscount);
        totalDiscount = applyPromotionCap(promotion, uncapped);

        if (totalDiscount.compareTo(uncapped) < 0 && uncapped.compareTo(BigDecimal.ZERO) > 0) {
            // Restore line states to pre-application snapshot
            for (int i = 0; i < eligibleLines.size(); i++) {
                eligibleLines.get(i).lineDiscount = beforeDiscounts.get(i);
            }
            // Re-apply with proportionally scaled-down discounts
            BigDecimal scaleFactor = totalDiscount.divide(uncapped, 10, RoundingMode.HALF_UP);
            BigDecimal reappliedTotal = BigDecimal.ZERO;
            for (int i = 0; i < eligibleLines.size(); i++) {
                BigDecimal scaled = normalizeMoney(appliedPerLine.get(i).multiply(scaleFactor));
                BigDecimal reapplied = eligibleLines.get(i).applyLineDiscount(scaled);
                reappliedTotal = reappliedTotal.add(reapplied);
            }
            totalDiscount = normalizeMoney(reappliedTotal);
        }

        if (totalDiscount.compareTo(BigDecimal.ZERO) <= 0) {
            return PromotionApplicationResult.rejected("Promotion produced no discount");
        }
        return PromotionApplicationResult.applied(totalDiscount);
    }

    private PromotionApplicationResult applyBuyXGetYLinePromotion(PromotionCampaign promotion, List<LineState> eligibleLines) {
        Integer buyQty = promotion.getBuyQuantity();
        Integer getQty = promotion.getGetQuantity();
        if (buyQty == null || buyQty < 1 || getQty == null || getQty < 1) {
            return PromotionApplicationResult.rejected("BUY_X_GET_Y promotion is missing buy/get quantities");
        }

        int bundleSize = buyQty + getQty;
        int totalEligibleQuantity = eligibleLines.stream()
                .filter(line -> line.request().quantity() > 0)
                .filter(line -> line.lineTotal().compareTo(BigDecimal.ZERO) > 0)
                .mapToInt(line -> line.request().quantity())
                .sum();
        if (totalEligibleQuantity < bundleSize) {
            return PromotionApplicationResult.rejected("Not enough eligible quantity for BUY_X_GET_Y");
        }

        int freeUnitsRemaining = (totalEligibleQuantity / bundleSize) * getQty;
        if (freeUnitsRemaining <= 0) {
            return PromotionApplicationResult.rejected("BUY_X_GET_Y produced no free units");
        }

        List<LineState> cheapestFirst = eligibleLines.stream()
                .sorted(Comparator
                        .comparing(this::bogoUnitSortPrice)
                        .thenComparing(line -> line.request().productId(), Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(line -> line.request().vendorId(), Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        List<BigDecimal> beforeDiscounts = cheapestFirst.stream()
                .map(l -> normalizeMoney(l.lineDiscount()))
                .toList();

        BigDecimal totalDiscount = BigDecimal.ZERO;
        List<BigDecimal> appliedPerLine = new ArrayList<>(cheapestFirst.size());
        for (LineState line : cheapestFirst) {
            if (freeUnitsRemaining <= 0) {
                appliedPerLine.add(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                continue;
            }
            if (line.request().quantity() <= 0 || line.lineTotal().compareTo(BigDecimal.ZERO) <= 0) {
                appliedPerLine.add(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                continue;
            }
            int freeUnitsForLine = Math.min(freeUnitsRemaining, line.request().quantity());
            if (freeUnitsForLine <= 0) {
                appliedPerLine.add(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                continue;
            }
            BigDecimal discount = bogoDiscountForUnits(line, freeUnitsForLine);
            if (discount.compareTo(BigDecimal.ZERO) <= 0) {
                appliedPerLine.add(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                continue;
            }
            BigDecimal applied = line.applyLineDiscount(discount);
            appliedPerLine.add(applied);
            if (applied.compareTo(BigDecimal.ZERO) > 0) {
                totalDiscount = totalDiscount.add(applied);
                freeUnitsRemaining -= freeUnitsForLine;
            }
        }

        BigDecimal uncapped = normalizeMoney(totalDiscount);
        totalDiscount = applyPromotionCap(promotion, uncapped);

        if (totalDiscount.compareTo(uncapped) < 0 && uncapped.compareTo(BigDecimal.ZERO) > 0) {
            for (int i = 0; i < cheapestFirst.size(); i++) {
                cheapestFirst.get(i).lineDiscount = beforeDiscounts.get(i);
            }
            BigDecimal scaleFactor = totalDiscount.divide(uncapped, 10, RoundingMode.HALF_UP);
            BigDecimal reappliedTotal = BigDecimal.ZERO;
            for (int i = 0; i < cheapestFirst.size(); i++) {
                BigDecimal scaled = normalizeMoney(appliedPerLine.get(i).multiply(scaleFactor));
                BigDecimal reapplied = cheapestFirst.get(i).applyLineDiscount(scaled);
                reappliedTotal = reappliedTotal.add(reapplied);
            }
            totalDiscount = normalizeMoney(reappliedTotal);
        }

        if (totalDiscount.compareTo(BigDecimal.ZERO) <= 0) {
            return PromotionApplicationResult.rejected("BUY_X_GET_Y produced no discount");
        }
        return PromotionApplicationResult.applied(totalDiscount);
    }

    private PromotionApplicationResult applyCartLevelPromotion(PromotionCampaign promotion, List<LineState> lineStates) {
        if (promotion.getBenefitType() == PromotionBenefitType.FREE_SHIPPING) {
            return PromotionApplicationResult.rejected("FREE_SHIPPING promotions must use SHIPPING application level");
        }
        if (promotion.getBenefitType() == PromotionBenefitType.BUY_X_GET_Y) {
            return PromotionApplicationResult.rejected("BUY_X_GET_Y promotions must use LINE_ITEM application level");
        }

        BigDecimal eligibleBase = eligibleLinesForPromotion(promotion, lineStates).stream()
                .map(LineState::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        eligibleBase = normalizeMoney(eligibleBase);
        if (eligibleBase.compareTo(BigDecimal.ZERO) <= 0) {
            return PromotionApplicationResult.rejected("No eligible cart amount remaining");
        }
        if (promotion.getBenefitType() == PromotionBenefitType.TIERED_SPEND) {
            return applyTieredSpendCartPromotion(promotion, eligibleBase);
        }
        if (promotion.getBenefitType() == PromotionBenefitType.BUNDLE_DISCOUNT) {
            return applyBundleDiscountCartPromotion(promotion, lineStates, eligibleBase);
        }

        BigDecimal discount = calculateDiscountForAmount(promotion, eligibleBase);
        discount = applyPromotionCap(promotion, discount);
        discount = minMoney(discount, eligibleBase);
        if (discount.compareTo(BigDecimal.ZERO) <= 0) {
            return PromotionApplicationResult.rejected("Promotion produced no discount");
        }
        return PromotionApplicationResult.applied(discount);
    }

    private PromotionApplicationResult applyTieredSpendCartPromotion(PromotionCampaign promotion, BigDecimal eligibleBase) {
        if (eligibleBase == null || eligibleBase.compareTo(BigDecimal.ZERO) <= 0) {
            return PromotionApplicationResult.rejected("No eligible cart amount remaining");
        }

        PromotionSpendTier matchedTier = highestMatchingSpendTier(promotion, eligibleBase);
        if (matchedTier == null) {
            return PromotionApplicationResult.rejected("No spend tier threshold met");
        }

        BigDecimal discount = normalizeMoney(matchedTier.getDiscountAmount());
        discount = applyPromotionCap(promotion, discount);
        discount = minMoney(discount, eligibleBase);
        if (discount.compareTo(BigDecimal.ZERO) <= 0) {
            return PromotionApplicationResult.rejected("Promotion produced no discount");
        }
        return PromotionApplicationResult.applied(discount);
    }

    private PromotionApplicationResult applyBundleDiscountCartPromotion(
            PromotionCampaign promotion,
            List<LineState> lineStates,
            BigDecimal eligibleBase
    ) {
        if (promotion.getScopeType() != PromotionScopeType.PRODUCT) {
            return PromotionApplicationResult.rejected("BUNDLE_DISCOUNT promotions must use PRODUCT scope");
        }
        Set<UUID> bundleProductIds = promotion.getTargetProductIds() == null ? Set.of() : Set.copyOf(promotion.getTargetProductIds());
        if (bundleProductIds.size() < 2) {
            return PromotionApplicationResult.rejected("BUNDLE_DISCOUNT requires at least 2 target products");
        }
        if (eligibleBase == null || eligibleBase.compareTo(BigDecimal.ZERO) <= 0) {
            return PromotionApplicationResult.rejected("No eligible bundle amount remaining");
        }

        int completeBundleCount = calculateCompleteBundleCount(bundleProductIds, lineStates);
        if (completeBundleCount <= 0) {
            return PromotionApplicationResult.rejected("No complete bundle found in cart");
        }

        BigDecimal perBundleDiscount = normalizeMoney(promotion.getBenefitValue());
        if (perBundleDiscount.compareTo(BigDecimal.ZERO) <= 0) {
            return PromotionApplicationResult.rejected("BUNDLE_DISCOUNT is missing benefitValue");
        }

        BigDecimal discount = normalizeMoney(perBundleDiscount.multiply(BigDecimal.valueOf(completeBundleCount)));
        discount = applyPromotionCap(promotion, discount);
        discount = minMoney(discount, eligibleBase);
        if (discount.compareTo(BigDecimal.ZERO) <= 0) {
            return PromotionApplicationResult.rejected("Promotion produced no discount");
        }
        return PromotionApplicationResult.applied(discount);
    }

    private PromotionSpendTier highestMatchingSpendTier(PromotionCampaign promotion, BigDecimal eligibleBase) {
        if (promotion == null || promotion.getSpendTiers() == null || promotion.getSpendTiers().isEmpty()) {
            return null;
        }
        BigDecimal normalizedBase = normalizeMoney(eligibleBase);
        return promotion.getSpendTiers().stream()
                .filter(Objects::nonNull)
                .filter(tier -> tier.getThresholdAmount() != null && tier.getDiscountAmount() != null)
                .filter(tier -> normalizeMoney(tier.getThresholdAmount()).compareTo(BigDecimal.ZERO) > 0)
                .filter(tier -> normalizeMoney(tier.getDiscountAmount()).compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator
                        .comparing((PromotionSpendTier tier) -> normalizeMoney(tier.getThresholdAmount())).reversed()
                        .thenComparing(tier -> normalizeMoney(tier.getDiscountAmount()), Comparator.reverseOrder()))
                .filter(tier -> normalizedBase.compareTo(normalizeMoney(tier.getThresholdAmount())) >= 0)
                .findFirst()
                .orElse(null);
    }

    private PromotionApplicationResult applyShippingPromotion(
            PromotionCampaign promotion,
            List<LineState> lineStates,
            BigDecimal shippingAmount,
            BigDecimal shippingDiscountAlreadyApplied
    ) {
        if (shippingAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return PromotionApplicationResult.rejected("No shipping amount to discount");
        }
        if (promotion.getScopeType() != PromotionScopeType.ORDER) {
            return PromotionApplicationResult.rejected("Scoped shipping promotions require shipping allocation support (not implemented yet)");
        }
        if (promotion.getBenefitType() == PromotionBenefitType.BUY_X_GET_Y) {
            return PromotionApplicationResult.rejected("BUY_X_GET_Y promotions must use LINE_ITEM application level");
        }
        if (promotion.getBenefitType() == PromotionBenefitType.BUNDLE_DISCOUNT) {
            return PromotionApplicationResult.rejected("BUNDLE_DISCOUNT promotions must use CART application level");
        }

        BigDecimal shippingRemaining = normalizeMoney(shippingAmount.subtract(shippingDiscountAlreadyApplied));
        if (shippingRemaining.compareTo(BigDecimal.ZERO) <= 0) {
            return PromotionApplicationResult.rejected("Shipping amount already fully discounted");
        }

        BigDecimal discount;
        if (promotion.getBenefitType() == PromotionBenefitType.FREE_SHIPPING) {
            discount = shippingRemaining;
        } else {
            discount = calculateDiscountForAmount(promotion, shippingRemaining);
        }
        discount = applyPromotionCap(promotion, discount);
        discount = minMoney(discount, shippingRemaining);
        if (discount.compareTo(BigDecimal.ZERO) <= 0) {
            return PromotionApplicationResult.rejected("Promotion produced no shipping discount");
        }
        return PromotionApplicationResult.applied(discount);
    }

    private List<LineState> eligibleLinesForPromotion(PromotionCampaign promotion, List<LineState> lineStates) {
        return lineStates.stream()
                .filter(line -> isLineEligible(promotion, line))
                .toList();
    }

    private boolean isLineEligible(PromotionCampaign promotion, LineState line) {
        return switch (promotion.getScopeType()) {
            case ORDER -> true;
            case VENDOR -> promotion.getVendorId() != null && promotion.getVendorId().equals(line.request().vendorId());
            case PRODUCT -> promotion.getTargetProductIds() != null && promotion.getTargetProductIds().contains(line.request().productId());
            case CATEGORY -> {
                Set<UUID> targets = promotion.getTargetCategoryIds() == null ? Set.of() : promotion.getTargetCategoryIds();
                Set<UUID> lineCategories = line.request().categoryIdsOrEmpty();
                yield !targets.isEmpty() && lineCategories.stream().anyMatch(targets::contains);
            }
        };
    }

    private BigDecimal calculateDiscountForAmount(PromotionCampaign promotion, BigDecimal baseAmount) {
        if (baseAmount == null || baseAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal discount = switch (promotion.getBenefitType()) {
            case PERCENTAGE_OFF -> {
                BigDecimal percent = promotion.getBenefitValue() == null ? BigDecimal.ZERO : promotion.getBenefitValue();
                if (percent.compareTo(BigDecimal.ZERO) <= 0) {
                    yield BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                }
                if (percent.compareTo(BigDecimal.valueOf(100)) > 0) {
                    percent = BigDecimal.valueOf(100);
                }
                yield baseAmount.multiply(percent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            }
            case FIXED_AMOUNT_OFF -> promotion.getBenefitValue() == null ? BigDecimal.ZERO : promotion.getBenefitValue();
            case FREE_SHIPPING -> BigDecimal.ZERO;
            case BUY_X_GET_Y -> BigDecimal.ZERO;
            case TIERED_SPEND -> BigDecimal.ZERO;
            case BUNDLE_DISCOUNT -> BigDecimal.ZERO;
        };
        return minMoney(normalizeMoney(discount), normalizeMoney(baseAmount));
    }

    private int calculateCompleteBundleCount(Set<UUID> bundleProductIds, List<LineState> lineStates) {
        if (bundleProductIds == null || bundleProductIds.isEmpty() || lineStates == null || lineStates.isEmpty()) {
            return 0;
        }

        Map<UUID, Integer> quantityByProduct = new LinkedHashMap<>();
        for (LineState line : lineStates) {
            if (line == null || line.request() == null) {
                continue;
            }
            UUID productId = line.request().productId();
            if (productId == null || !bundleProductIds.contains(productId)) {
                continue;
            }
            if (line.request().quantity() <= 0 || line.lineTotal().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            quantityByProduct.merge(productId, line.request().quantity(), Integer::sum);
        }

        int bundleCount = Integer.MAX_VALUE;
        for (UUID productId : bundleProductIds) {
            Integer qty = quantityByProduct.get(productId);
            if (qty == null || qty < 1) {
                return 0;
            }
            bundleCount = Math.min(bundleCount, qty);
        }
        return bundleCount == Integer.MAX_VALUE ? 0 : bundleCount;
    }

    private BigDecimal bogoUnitSortPrice(LineState line) {
        if (line == null || line.request() == null || line.request().quantity() <= 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        if (line.lineTotal().compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return line.lineTotal().divide(BigDecimal.valueOf(line.request().quantity()), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal bogoDiscountForUnits(LineState line, int freeUnits) {
        if (line == null || freeUnits <= 0 || line.request() == null || line.request().quantity() <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (line.lineTotal().compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal proportional = line.lineTotal()
                .multiply(BigDecimal.valueOf(freeUnits))
                .divide(BigDecimal.valueOf(line.request().quantity()), 2, RoundingMode.HALF_UP);
        return minMoney(proportional, line.lineTotal());
    }

    private BigDecimal applyPromotionCap(PromotionCampaign promotion, BigDecimal discount) {
        BigDecimal normalized = normalizeMoney(discount == null ? BigDecimal.ZERO : discount);
        BigDecimal cap = promotion.getMaximumDiscountAmount();
        if (cap == null) {
            return normalized;
        }
        return minMoney(normalized, normalizeMoney(cap));
    }

    private boolean withinWindow(PromotionCampaign promotion, Instant now) {
        if (promotion.getStartsAt() != null && promotion.getStartsAt().isAfter(now)) {
            return false;
        }
        return promotion.getEndsAt() == null || !promotion.getEndsAt().isBefore(now);
    }

    private boolean matchesSegment(PromotionCampaign promotion, String customerSegment) {
        String segments = promotion.getTargetSegments();
        if (segments == null || segments.isBlank()) {
            return true; // no segment restriction
        }
        if (customerSegment == null || customerSegment.isBlank()) {
            return false; // promotion requires a segment but none provided
        }
        String normalizedCustomerSegment = customerSegment.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(segments.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toUpperCase(Locale.ROOT))
                .anyMatch(s -> s.equals(normalizedCustomerSegment));
    }

    private boolean withinFlashSaleWindow(PromotionCampaign promotion, Instant now) {
        if (!promotion.isFlashSale()) {
            return true; // not a flash sale, no extra window check
        }
        if (promotion.getFlashSaleStartAt() != null && promotion.getFlashSaleStartAt().isAfter(now)) {
            return false;
        }
        return promotion.getFlashSaleEndAt() == null || !promotion.getFlashSaleEndAt().isBefore(now);
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal minMoney(BigDecimal a, BigDecimal b) {
        BigDecimal left = normalizeMoney(a);
        BigDecimal right = normalizeMoney(b);
        return left.compareTo(right) <= 0 ? left : right;
    }

    private BigDecimal remainingBudgetForQuote(PromotionCampaign promotion) {
        if (promotion == null || promotion.getBudgetAmount() == null) {
            return null;
        }
        BigDecimal budget = normalizeMoney(promotion.getBudgetAmount());
        BigDecimal burned = normalizeMoney(promotion.getBurnedBudgetAmount());
        BigDecimal remaining = normalizeMoney(budget.subtract(burned));
        return remaining.compareTo(BigDecimal.ZERO) < 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : remaining;
    }

    private List<BigDecimal> snapshotLineDiscounts(List<LineState> lineStates) {
        if (lineStates == null || lineStates.isEmpty()) {
            return List.of();
        }
        return lineStates.stream()
                .map(LineState::lineDiscount)
                .map(this::normalizeMoney)
                .toList();
    }

    private void restoreLineDiscounts(List<LineState> lineStates, List<BigDecimal> snapshot) {
        if (lineStates == null || snapshot == null || lineStates.size() != snapshot.size()) {
            return;
        }
        for (int i = 0; i < lineStates.size(); i++) {
            lineStates.get(i).lineDiscount = normalizeMoney(snapshot.get(i));
        }
    }

    private static final class LineState {
        private final PromotionQuoteLineRequest request;
        private final BigDecimal unitPrice;
        private final BigDecimal lineSubtotal;
        private BigDecimal lineDiscount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        private LineState(PromotionQuoteLineRequest request, BigDecimal unitPrice, BigDecimal lineSubtotal) {
            this.request = Objects.requireNonNull(request);
            this.unitPrice = unitPrice;
            this.lineSubtotal = lineSubtotal;
        }

        public PromotionQuoteLineRequest request() {
            return request;
        }

        public BigDecimal unitPrice() {
            return unitPrice;
        }

        public BigDecimal lineSubtotal() {
            return lineSubtotal;
        }

        public BigDecimal lineDiscount() {
            return lineDiscount;
        }

        public BigDecimal lineTotal() {
            BigDecimal total = lineSubtotal.subtract(lineDiscount);
            return total.compareTo(BigDecimal.ZERO) < 0
                    ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                    : total.setScale(2, RoundingMode.HALF_UP);
        }

        public BigDecimal applyLineDiscount(BigDecimal discount) {
            BigDecimal normalized = discount == null ? BigDecimal.ZERO : discount.setScale(2, RoundingMode.HALF_UP);
            if (normalized.compareTo(BigDecimal.ZERO) <= 0) {
                return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            }
            BigDecimal maxAllowed = lineTotal();
            BigDecimal applied = normalized.compareTo(maxAllowed) > 0 ? maxAllowed : normalized;
            this.lineDiscount = this.lineDiscount.add(applied).setScale(2, RoundingMode.HALF_UP);
            return applied;
        }
    }

    private record PromotionApplicationResult(boolean applied, BigDecimal discountAmount, String reason) {
        private static PromotionApplicationResult applied(BigDecimal discountAmount) {
            return new PromotionApplicationResult(true, discountAmount == null ? BigDecimal.ZERO : discountAmount.setScale(2, RoundingMode.HALF_UP), null);
        }

        private static PromotionApplicationResult rejected(String reason) {
            return new PromotionApplicationResult(false, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), reason);
        }
    }

    private record PromotionCandidate(
            PromotionCampaign promotion,
            boolean explicitCoupon,
            String couponCode
    ) {
    }
}
