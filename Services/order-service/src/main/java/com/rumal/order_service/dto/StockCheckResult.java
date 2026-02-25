package com.rumal.order_service.dto;

import java.util.UUID;

public record StockCheckResult(
        UUID productId,
        int totalAvailable,
        boolean sufficient,
        boolean backorderable,
        String stockStatus
) {}
