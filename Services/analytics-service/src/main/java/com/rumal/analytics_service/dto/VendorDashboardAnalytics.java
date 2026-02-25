package com.rumal.analytics_service.dto;

import com.rumal.analytics_service.client.dto.*;

import java.util.List;

public record VendorDashboardAnalytics(
        VendorOrderSummary orderSummary,
        List<DailyRevenueBucket> revenueTrend,
        List<TopProductEntry> topProducts,
        VendorProductSummary productSummary,
        VendorInventoryHealth inventoryHealth,
        VendorPromotionSummary promotionSummary,
        VendorReviewSummary reviewSummary,
        VendorPerformanceSummary performance
) {}
