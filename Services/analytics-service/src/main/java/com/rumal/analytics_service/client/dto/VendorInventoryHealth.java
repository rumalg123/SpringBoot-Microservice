package com.rumal.analytics_service.client.dto;

import java.util.UUID;

public record VendorInventoryHealth(
        UUID vendorId,
        long totalSkus,
        long inStockCount,
        long lowStockCount,
        long outOfStockCount
) {}
