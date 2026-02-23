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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

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

        Instant pricedAt = request.pricingAt() == null ? Instant.now() : request.pricingAt();
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

        Map<UUID, PromotionCandidate> candidatesById = new LinkedHashMap<>();
        promotionCampaignRepository.findAll().stream()
                .filter(p -> p.getLifecycleStatus() == PromotionLifecycleStatus.ACTIVE)
                .filter(this::isApprovalEligible)
                .filter(p -> withinWindow(p, pricedAt))
                .forEach(p -> candidatesById.put(p.getId(), new PromotionCandidate(p, false, null)));
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
        BigDecimal cartDiscountTotal = BigDecimal.ZERO;
        BigDecimal shippingDiscountTotal = BigDecimal.ZERO;

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

            PromotionApplicationResult result = applyPromotion(promotion, lineStates, subtotal, shippingAmount, shippingDiscountTotal);
            if (!result.applied()) {
                rejected.add(new RejectedPromotionQuoteEntry(promotion.getId(), promotion.getName(), result.reason()));
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

        BigDecimal totalDiscount = BigDecimal.ZERO;
        for (LineState line : eligibleLines) {
            BigDecimal remaining = line.lineTotal();
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal discount = calculateDiscountForAmount(promotion, remaining);
            if (discount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            line.applyLineDiscount(discount);
            totalDiscount = totalDiscount.add(discount);
        }
        totalDiscount = applyPromotionCap(promotion, normalizeMoney(totalDiscount));
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

        BigDecimal totalDiscount = BigDecimal.ZERO;
        for (LineState line : cheapestFirst) {
            if (freeUnitsRemaining <= 0) {
                break;
            }
            if (line.request().quantity() <= 0 || line.lineTotal().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            int freeUnitsForLine = Math.min(freeUnitsRemaining, line.request().quantity());
            if (freeUnitsForLine <= 0) {
                continue;
            }
            BigDecimal discount = bogoDiscountForUnits(line, freeUnitsForLine);
            if (discount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal applied = line.applyLineDiscount(discount);
            if (applied.compareTo(BigDecimal.ZERO) > 0) {
                totalDiscount = totalDiscount.add(applied);
                freeUnitsRemaining -= freeUnitsForLine;
            }
        }

        totalDiscount = applyPromotionCap(promotion, normalizeMoney(totalDiscount));
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
                yield baseAmount.multiply(percent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            }
            case FIXED_AMOUNT_OFF -> promotion.getBenefitValue() == null ? BigDecimal.ZERO : promotion.getBenefitValue();
            case FREE_SHIPPING -> BigDecimal.ZERO;
            case BUY_X_GET_Y -> BigDecimal.ZERO;
            case TIERED_SPEND -> BigDecimal.ZERO;
        };
        return minMoney(normalizeMoney(discount), normalizeMoney(baseAmount));
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

    private boolean isApprovalEligible(PromotionCampaign promotion) {
        PromotionApprovalStatus status = promotion.getApprovalStatus();
        return status == PromotionApprovalStatus.NOT_REQUIRED || status == PromotionApprovalStatus.APPROVED;
    }

    private boolean withinWindow(PromotionCampaign promotion, Instant now) {
        if (promotion.getStartsAt() != null && promotion.getStartsAt().isAfter(now)) {
            return false;
        }
        return promotion.getEndsAt() == null || !promotion.getEndsAt().isBefore(now);
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal minMoney(BigDecimal a, BigDecimal b) {
        BigDecimal left = normalizeMoney(a);
        BigDecimal right = normalizeMoney(b);
        return left.compareTo(right) <= 0 ? left : right;
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
