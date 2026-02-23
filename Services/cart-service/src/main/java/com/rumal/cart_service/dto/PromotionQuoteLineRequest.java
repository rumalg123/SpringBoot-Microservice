package com.rumal.cart_service.dto;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

public record PromotionQuoteLineRequest(
        UUID productId,
        UUID vendorId,
        Set<UUID> categoryIds,
        BigDecimal unitPrice,
        int quantity
) {
}
