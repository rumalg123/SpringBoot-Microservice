package com.rumal.payment_service.dto;

import java.util.UUID;

public record InventoryReservationReadinessResponse(
        UUID orderId,
        boolean readyForPayment
) {
}
