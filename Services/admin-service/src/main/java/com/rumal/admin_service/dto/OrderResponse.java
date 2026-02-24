package com.rumal.admin_service.dto;

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
        String currency,
        BigDecimal subtotal,
        BigDecimal lineDiscountTotal,
        BigDecimal cartDiscountTotal,
        BigDecimal shippingAmount,
        BigDecimal shippingDiscountTotal,
        BigDecimal totalDiscount,
        String couponCode,
        UUID couponReservationId,
        String paymentId,
        String paymentMethod,
        String paymentGatewayRef,
        Instant paidAt,
        String customerNote,
        String status,
        Instant expiresAt,
        BigDecimal refundAmount,
        String refundReason,
        Instant refundInitiatedAt,
        Instant refundCompletedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
