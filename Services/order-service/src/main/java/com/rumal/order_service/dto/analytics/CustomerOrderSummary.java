package com.rumal.order_service.dto.analytics;

import java.math.BigDecimal;
import java.util.UUID;

public record CustomerOrderSummary(
    UUID customerId,
    long totalOrders,
    long activeOrders,
    long completedOrders,
    BigDecimal totalSpent,
    BigDecimal totalSaved,
    BigDecimal averageOrderValue,
    long uniqueVendorsOrdered
) {}
