package com.rumal.inventory_service.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record StockItemUpdateRequest(
        @Size(max = 80) String sku,
        @Min(0) Integer lowStockThreshold,
        Boolean backorderable
) {}
