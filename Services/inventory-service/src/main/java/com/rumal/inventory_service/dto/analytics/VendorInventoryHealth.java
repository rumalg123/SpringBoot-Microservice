package com.rumal.inventory_service.dto.analytics;

import java.util.UUID;

public record VendorInventoryHealth(
    UUID vendorId,
    long totalSkus,
    long inStockCount,
    long lowStockCount,
    long outOfStockCount
) {}
