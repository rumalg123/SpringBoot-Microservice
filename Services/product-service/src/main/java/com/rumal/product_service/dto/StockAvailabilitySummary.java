package com.rumal.product_service.dto;

import java.util.UUID;

public record StockAvailabilitySummary(
        UUID productId,
        int totalAvailable,
        int totalOnHand,
        int totalReserved,
        boolean backorderable,
        String stockStatus
) {}
