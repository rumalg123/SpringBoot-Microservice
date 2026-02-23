package com.rumal.order_service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderDetailsResponse(
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
        Instant createdAt,
        List<OrderItemResponse> items,
        OrderAddressResponse shippingAddress,
        OrderAddressResponse billingAddress,

        CustomerSummary customer,     // can be null in GRACEFUL mode
        List<String> warnings         // can be empty
) {}
