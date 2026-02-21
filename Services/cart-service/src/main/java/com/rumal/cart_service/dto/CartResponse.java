package com.rumal.cart_service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CartResponse(
        UUID id,
        String keycloakId,
        List<CartItemResponse> items,
        int itemCount,
        int totalQuantity,
        BigDecimal subtotal,
        Instant createdAt,
        Instant updatedAt
) {
}
