package com.rumal.product_service.dto.analytics;

public record ProductPlatformSummary(
    long totalProducts,
    long activeProducts,
    long draftProducts,
    long pendingApproval,
    long totalViews,
    long totalSold
) {}
