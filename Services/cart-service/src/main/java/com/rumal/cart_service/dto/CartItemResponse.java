package com.rumal.cart_service.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CartItemResponse(
        UUID id,
        UUID productId,
        String productSlug,
        String productName,
        String productSku,
        String mainImage,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal
) {
}
