package com.rumal.order_service.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record PromotionQuoteRequest(
        List<LineItem> lines,
        BigDecimal shippingAmount,
        UUID customerId,
        String couponCode,
        String countryCode
) {
    public record LineItem(
            UUID productId,
            UUID vendorId,
            Set<UUID> categoryIds,
            BigDecimal unitPrice,
            int quantity
    ) {}
}
