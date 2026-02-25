package com.rumal.analytics_service.client.dto;

import java.util.UUID;

public record LowStockAlert(
        UUID productId,
        UUID vendorId,
        String sku,
        int quantityAvailable,
        int lowStockThreshold,
        String stockStatus
) {}
