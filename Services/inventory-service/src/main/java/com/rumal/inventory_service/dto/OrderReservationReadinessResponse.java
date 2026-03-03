package com.rumal.inventory_service.dto;

import java.util.UUID;

public record OrderReservationReadinessResponse(
        UUID orderId,
        boolean readyForPayment
) {
}
