package com.rumal.inventory_service.dto;

import java.time.Instant;
import java.util.UUID;

public record StockReservationDetailResponse(
        UUID id,
        UUID orderId,
        UUID productId,
        UUID stockItemId,
        UUID warehouseId,
        int quantityReserved,
        String status,
        Instant reservedAt,
        Instant expiresAt,
        Instant confirmedAt,
        Instant releasedAt,
        String releaseReason,
        Instant createdAt
) {}
