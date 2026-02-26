package com.rumal.promotion_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PromotionQuoteRequest(
        @NotEmpty @Size(max = 200) List<@Valid PromotionQuoteLineRequest> lines,
        @DecimalMin(value = "0.00", message = "shippingAmount cannot be negative") BigDecimal shippingAmount,
        UUID customerId,
        @Size(max = 40) String customerSegment,
        @Size(max = 64) String couponCode,
        @Size(max = 8) String countryCode,
        Instant pricingAt
) {
}
