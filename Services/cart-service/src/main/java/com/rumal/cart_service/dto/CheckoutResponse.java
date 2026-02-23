package com.rumal.cart_service.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CheckoutResponse(
        UUID orderId,
        int itemCount,
        int totalQuantity,
        String couponCode,
        UUID couponReservationId,
        BigDecimal subtotal,
        BigDecimal lineDiscountTotal,
        BigDecimal cartDiscountTotal,
        BigDecimal shippingAmount,
        BigDecimal shippingDiscountTotal,
        BigDecimal totalDiscount,
        BigDecimal grandTotal,
        boolean cartCleared
) {
}
