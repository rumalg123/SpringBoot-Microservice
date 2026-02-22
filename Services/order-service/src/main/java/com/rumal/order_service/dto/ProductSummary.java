package com.rumal.order_service.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductSummary(
        UUID id,
        UUID parentProductId,
        UUID vendorId,
        String name,
        String sku,
        String productType,
        BigDecimal sellingPrice,
        boolean active
) {}
