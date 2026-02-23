package com.rumal.promotion_service.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateCouponCodeRequest(
        @NotBlank @Size(max = 64) String code,
        @Min(value = 1, message = "maxUses must be at least 1") Integer maxUses,
        @Min(value = 1, message = "maxUsesPerCustomer must be at least 1") Integer maxUsesPerCustomer,
        @Min(value = 60, message = "reservationTtlSeconds must be at least 60") Integer reservationTtlSeconds,
        Instant startsAt,
        Instant endsAt,
        Boolean active
) {
    @AssertTrue(message = "startsAt must be before or equal to endsAt")
    public boolean isDateRangeValid() {
        return startsAt == null || endsAt == null || !startsAt.isAfter(endsAt);
    }
}
