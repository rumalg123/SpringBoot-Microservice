package com.rumal.inventory_service.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StockReservationResponse(
        UUID orderId,
        String status,
        List<ReservationItemResponse> items,
        Instant expiresAt
) {}
