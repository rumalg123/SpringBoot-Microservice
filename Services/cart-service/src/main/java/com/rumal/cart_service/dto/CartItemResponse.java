package com.rumal.cart_service.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CartItemResponse(
        UUID id,
        UUID productId,
        String productSlug,
        String productName,
        String productSku,
        String mainImage,
        List<UUID> categoryIds,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal,
        boolean savedForLater
) {
}
