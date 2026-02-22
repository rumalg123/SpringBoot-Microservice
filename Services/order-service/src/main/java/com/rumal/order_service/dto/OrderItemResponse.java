package com.rumal.order_service.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(
        UUID id,
        UUID productId,
        UUID vendorId,
        String productSku,
        String item,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {}
