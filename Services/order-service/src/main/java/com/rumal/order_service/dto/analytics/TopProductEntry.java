package com.rumal.order_service.dto.analytics;

import java.math.BigDecimal;
import java.util.UUID;

public record TopProductEntry(UUID productId, String productName, UUID vendorId, long quantitySold, BigDecimal totalRevenue) {}
