package com.rumal.promotion_service.dto;

import com.rumal.promotion_service.entity.PromotionApplicationLevel;
import com.rumal.promotion_service.entity.PromotionBenefitType;
import com.rumal.promotion_service.entity.PromotionFundingSource;
import com.rumal.promotion_service.entity.PromotionScopeType;
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

    @AssertTrue(message = "startsAt must be before or equal to endsAt")
    public boolean isDateRangeValid() {
        return startsAt == null || endsAt == null || !startsAt.isAfter(endsAt);
    }

    @AssertTrue(message = "stackable and exclusive cannot both be true")
    public boolean isStackingFlagsValid() {
        return !(stackable && exclusive);
    }
}
