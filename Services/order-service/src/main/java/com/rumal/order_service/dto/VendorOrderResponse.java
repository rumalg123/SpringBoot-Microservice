package com.rumal.order_service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record VendorOrderResponse(
        UUID id,
        UUID orderId,
        UUID vendorId,
        String status,
        int itemCount,
        int quantity,
        BigDecimal orderTotal,
        Instant createdAt
) {
}
