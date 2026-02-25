package com.rumal.inventory_service.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record BatchStockSummaryRequest(
        @NotEmpty List<UUID> productIds
) {}
