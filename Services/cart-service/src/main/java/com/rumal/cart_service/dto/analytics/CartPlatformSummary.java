package com.rumal.cart_service.dto.analytics;

import java.math.BigDecimal;

public record CartPlatformSummary(
    long totalActiveCarts,
    long totalCartItems,
    long totalSavedForLater,
    BigDecimal avgCartValue,
    double avgItemsPerCart
) {}
