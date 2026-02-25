package com.rumal.inventory_service.dto.analytics;

public record InventoryHealthSummary(
    long totalSkus,
    long inStockCount,
    long lowStockCount,
    long outOfStockCount,
    long backorderCount,
    long totalQuantityOnHand,
    long totalQuantityReserved
) {}
