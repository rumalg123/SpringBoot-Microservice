package com.rumal.inventory_service.dto;

import java.time.Instant;
import java.util.UUID;

public record StockItemResponse(
        UUID id,
        UUID productId,
        UUID vendorId,
        UUID warehouseId,
        String warehouseName,
        String sku,
        int quantityOnHand,
        int quantityReserved,
        int quantityAvailable,
        int lowStockThreshold,
        boolean backorderable,
        String stockStatus,
        Instant createdAt,
        Instant updatedAt
) {}
