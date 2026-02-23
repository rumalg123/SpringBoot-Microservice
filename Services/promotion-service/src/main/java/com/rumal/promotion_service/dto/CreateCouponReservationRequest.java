package com.rumal.promotion_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateCouponReservationRequest(
        @NotBlank @Size(max = 64) String couponCode,
        @NotNull UUID customerId,
        @Valid @NotNull PromotionQuoteRequest quoteRequest,
        @Size(max = 120) String requestKey
) {
}
