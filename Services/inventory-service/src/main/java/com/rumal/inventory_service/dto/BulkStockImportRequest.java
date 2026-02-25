package com.rumal.inventory_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BulkStockImportRequest(
        @NotEmpty @Valid List<StockItemCreateRequest> items
) {}
