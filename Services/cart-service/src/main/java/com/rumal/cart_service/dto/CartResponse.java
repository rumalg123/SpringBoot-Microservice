package com.rumal.cart_service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CartResponse(
        UUID id,
        String keycloakId,
        List<CartItemResponse> items,
        List<CartItemResponse> savedForLaterItems,
        int itemCount,
        int totalQuantity,
        BigDecimal subtotal,
        String note,
        Instant createdAt,
        Instant updatedAt
) {
}
