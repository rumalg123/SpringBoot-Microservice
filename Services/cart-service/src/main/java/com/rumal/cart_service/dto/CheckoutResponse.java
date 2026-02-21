package com.rumal.cart_service.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CheckoutResponse(
        UUID orderId,
        int itemCount,
        int totalQuantity,
        BigDecimal subtotal
) {
}
