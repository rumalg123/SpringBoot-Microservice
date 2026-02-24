package com.rumal.order_service.dto;

import com.rumal.order_service.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateOrderStatusRequest(
        @NotNull OrderStatus status,
        @Size(max = 500) String reason,
        String refundReason,
        BigDecimal refundAmount,
        Integer refundedQuantity
) {
}
