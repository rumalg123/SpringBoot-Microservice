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
        String customerNote,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
