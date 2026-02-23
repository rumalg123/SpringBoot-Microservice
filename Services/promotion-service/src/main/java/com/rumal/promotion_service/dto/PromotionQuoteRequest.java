package com.rumal.promotion_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PromotionQuoteRequest(
        @NotEmpty List<@Valid PromotionQuoteLineRequest> lines,
        @DecimalMin(value = "0.00", message = "shippingAmount cannot be negative") BigDecimal shippingAmount,
        UUID customerId,
        String couponCode,
        String countryCode,
        Instant pricingAt
) {
}
