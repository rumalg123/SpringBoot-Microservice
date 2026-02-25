package com.rumal.inventory_service.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record StockItemCreateRequest(
        @NotNull UUID productId,
        @NotNull UUID vendorId,
        @NotNull UUID warehouseId,
        @Size(max = 80) String sku,
        @Min(0) int quantityOnHand,
        @Min(0) int lowStockThreshold,
        boolean backorderable
) {}
