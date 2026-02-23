package com.rumal.order_service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID customerId,
        String item,
        int quantity,
        int itemCount,
        BigDecimal orderTotal,
        BigDecimal subtotal,
        BigDecimal lineDiscountTotal,
        BigDecimal cartDiscountTotal,
        BigDecimal shippingAmount,
        BigDecimal shippingDiscountTotal,
        BigDecimal totalDiscount,
        String couponCode,
        UUID couponReservationId,
        String status,
        Instant createdAt
) {}
