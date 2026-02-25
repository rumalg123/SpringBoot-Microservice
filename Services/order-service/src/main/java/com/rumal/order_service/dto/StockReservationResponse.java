package com.rumal.order_service.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StockReservationResponse(
        UUID orderId,
        String status,
        List<ReservationItemResponse> items,
        Instant expiresAt
) {
    public record ReservationItemResponse(
            UUID reservationId,
            UUID productId,
            UUID warehouseId,
            int quantityReserved
    ) {}
}
