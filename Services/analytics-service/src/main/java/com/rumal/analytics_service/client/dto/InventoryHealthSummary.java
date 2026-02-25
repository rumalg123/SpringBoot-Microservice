package com.rumal.analytics_service.client.dto;

public record InventoryHealthSummary(
        long totalSkus,
        long inStockCount,
        long lowStockCount,
        long outOfStockCount,
        long backorderCount,
        long totalQuantityOnHand,
        long totalQuantityReserved
) {}
