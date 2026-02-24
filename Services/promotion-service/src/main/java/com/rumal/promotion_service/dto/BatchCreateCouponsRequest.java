package com.rumal.promotion_service.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record BatchCreateCouponsRequest(
        @NotBlank(message = "prefix is required")
        @Size(max = 10, message = "prefix must be at most 10 characters")
        String prefix,

        @Min(value = 1, message = "count must be at least 1")
        @Max(value = 10000, message = "count must be at most 10000")
        int count,

        @Min(value = 1, message = "maxUses must be at least 1")
        Integer maxUses,

        @Min(value = 1, message = "maxUsesPerCustomer must be at least 1")
        Integer maxUsesPerCustomer,

        @Min(value = 60, message = "reservationTtlSeconds must be at least 60")
        Integer reservationTtlSeconds,

        Instant startsAt,
        Instant endsAt,
        Boolean active
) {
    @AssertTrue(message = "startsAt must be before endsAt")
    public boolean isDateRangeValid() {
        if (startsAt == null || endsAt == null) return true;
        return startsAt.isBefore(endsAt);
    }
}
