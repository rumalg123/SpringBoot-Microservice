package com.rumal.admin_service.dto;

import java.time.Instant;
import java.util.Map;

public record DashboardSummaryResponse(
        long totalOrders,
        long pendingOrders,
        long processingOrders,
        long completedOrders,
        long cancelledOrders,
        long totalVendors,
        long activeVendors,
        long totalProducts,
        long activePromotions,
        Map<String, Long> ordersByStatus,
        Instant generatedAt
) {
}
