package com.rumal.inventory_service.dto;

import java.time.Instant;
import java.util.UUID;

public record StockMovementResponse(
        UUID id,
        UUID stockItemId,
        UUID productId,
        UUID warehouseId,
        String movementType,
        int quantityChange,
        int quantityBefore,
        int quantityAfter,
        String referenceType,
        UUID referenceId,
        String actorType,
        String actorId,
        String note,
        Instant createdAt
) {}
