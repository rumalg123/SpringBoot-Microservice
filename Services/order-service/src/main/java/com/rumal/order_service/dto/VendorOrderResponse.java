package com.rumal.order_service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record VendorOrderResponse(
        UUID id,
        UUID orderId,
        UUID vendorId,
        String status,
        int itemCount,
        int quantity,
        BigDecimal orderTotal,
        String currency,
        BigDecimal discountAmount,
        BigDecimal shippingAmount,
        BigDecimal platformFee,
        BigDecimal payoutAmount,
        String trackingNumber,
        String trackingUrl,
        String carrierCode,
        LocalDate estimatedDeliveryDate,
        BigDecimal refundAmount,
        BigDecimal refundedAmount,
        Integer refundedQuantity,
        String refundReason,
        Instant refundInitiatedAt,
        Instant refundCompletedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
