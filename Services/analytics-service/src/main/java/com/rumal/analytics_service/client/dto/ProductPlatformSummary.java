package com.rumal.analytics_service.client.dto;

public record ProductPlatformSummary(
        long totalProducts,
        long activeProducts,
        long draftProducts,
        long pendingApproval,
        long totalViews,
        long totalSold
) {}
