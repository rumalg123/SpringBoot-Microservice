package com.rumal.analytics_service.client.dto;

import java.math.BigDecimal;

public record PlatformOrderSummary(
        long totalOrders,
        long pendingOrders,
        long processingOrders,
        long shippedOrders,
        long deliveredOrders,
        long cancelledOrders,
        long refundedOrders,
        BigDecimal totalRevenue,
        BigDecimal totalDiscount,
        BigDecimal totalShipping,
        BigDecimal averageOrderValue,
        double orderCompletionRate
) {}
