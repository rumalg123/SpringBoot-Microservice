package com.rumal.order_service.dto;

import java.util.UUID;

public record ProductSummary(
        UUID id,
        UUID parentProductId,
        String name,
        String sku,
        String productType,
        boolean active
) {}

