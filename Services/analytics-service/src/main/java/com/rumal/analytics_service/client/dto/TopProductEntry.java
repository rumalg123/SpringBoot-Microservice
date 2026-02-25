package com.rumal.analytics_service.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TopProductEntry(
        UUID productId,
        String productName,
        UUID vendorId,
        long quantitySold,
        BigDecimal totalRevenue
) {}
