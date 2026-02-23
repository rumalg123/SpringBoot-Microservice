package com.rumal.cart_service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PromotionQuoteRequest(
        List<PromotionQuoteLineRequest> lines,
        BigDecimal shippingAmount,
        UUID customerId,
        String couponCode,
        String countryCode,
        Instant pricingAt
) {
}
