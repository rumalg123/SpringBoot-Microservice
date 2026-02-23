package com.rumal.promotion_service.dto;

import com.rumal.promotion_service.entity.PromotionApplicationLevel;
import com.rumal.promotion_service.entity.PromotionBenefitType;
import com.rumal.promotion_service.entity.PromotionFundingSource;
import com.rumal.promotion_service.entity.PromotionScopeType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record UpsertPromotionRequest(
        @NotBlank @Size(max = 180) String name,
        @NotBlank @Size(max = 1500) String description,
        UUID vendorId,
        @NotNull PromotionScopeType scopeType,
        Set<UUID> targetProductIds,
        Set<UUID> targetCategoryIds,
        @NotNull PromotionApplicationLevel applicationLevel,
        @NotNull PromotionBenefitType benefitType,
        @DecimalMin(value = "0.00", message = "benefitValue cannot be negative") BigDecimal benefitValue,
        @Min(value = 1, message = "buyQuantity must be at least 1") Integer buyQuantity,
        @Min(value = 1, message = "getQuantity must be at least 1") Integer getQuantity,
        List<@Valid PromotionSpendTierRequest> spendTiers,
        @DecimalMin(value = "0.00", message = "minimumOrderAmount cannot be negative") BigDecimal minimumOrderAmount,
        @DecimalMin(value = "0.00", message = "maximumDiscountAmount cannot be negative") BigDecimal maximumDiscountAmount,
        @NotNull PromotionFundingSource fundingSource,
        boolean stackable,
        boolean exclusive,
        boolean autoApply,
        @NotNull @Min(value = 0, message = "priority must be 0 or greater") @Max(value = 10000, message = "priority must be 10000 or less") Integer priority,
        Instant startsAt,
        Instant endsAt
) {
    public Set<UUID> targetProductIdsOrEmpty() {
        return targetProductIds == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(targetProductIds));
    }

    public Set<UUID> targetCategoryIdsOrEmpty() {
        return targetCategoryIds == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(targetCategoryIds));
    }

    public List<PromotionSpendTierRequest> spendTiersOrEmpty() {
        return spendTiers == null ? List.of() : List.copyOf(spendTiers);
    }

    @AssertTrue(message = "startsAt must be before or equal to endsAt")
    public boolean isDateRangeValid() {
        return startsAt == null || endsAt == null || !startsAt.isAfter(endsAt);
    }

    @AssertTrue(message = "stackable and exclusive cannot both be true")
    public boolean isStackingFlagsValid() {
        return !(stackable && exclusive);
    }

    @AssertTrue(message = "BUY_X_GET_Y requires buyQuantity and getQuantity; other benefit types must not set them")
    public boolean isBogoFieldsValid() {
        if (benefitType == PromotionBenefitType.BUY_X_GET_Y) {
            return buyQuantity != null && buyQuantity > 0 && getQuantity != null && getQuantity > 0;
        }
        return buyQuantity == null && getQuantity == null;
    }

    @AssertTrue(message = "TIERED_SPEND requires spendTiers; other benefit types must not set spendTiers")
    public boolean isTieredSpendFieldsValid() {
        if (benefitType == PromotionBenefitType.TIERED_SPEND) {
            return spendTiersOrEmpty().stream().anyMatch(t -> t != null);
        }
        return spendTiers == null || spendTiers.isEmpty();
    }
}
