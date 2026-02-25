package com.rumal.analytics_service.client.dto;

import java.math.BigDecimal;

public record CartPlatformSummary(
        long totalActiveCarts,
        long totalCartItems,
        long totalSavedForLater,
        BigDecimal avgCartValue,
        double avgItemsPerCart
) {}
