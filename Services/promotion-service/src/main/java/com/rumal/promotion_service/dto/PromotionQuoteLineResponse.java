package com.rumal.promotion_service.dto;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

public record PromotionQuoteLineResponse(
        UUID productId,
        UUID vendorId,
        Set<UUID> categoryIds,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineSubtotal,
        BigDecimal lineDiscount,
        BigDecimal lineTotal
) {
}
