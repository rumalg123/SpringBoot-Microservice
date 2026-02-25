package com.rumal.analytics_service.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record VendorOrderSummary(
        UUID vendorId,
        long totalOrders,
        long activeOrders,
        long completedOrders,
        long cancelledOrders,
        long refundedOrders,
        BigDecimal totalRevenue,
        BigDecimal totalPlatformFees,
        BigDecimal totalPayouts,
        BigDecimal averageOrderValue
) {}
