package com.rumal.inventory_service.dto.analytics;

import java.util.UUID;

public record LowStockAlert(
    UUID productId,
    UUID vendorId,
    String sku,
    int quantityAvailable,
    int lowStockThreshold,
    String stockStatus
) {}
