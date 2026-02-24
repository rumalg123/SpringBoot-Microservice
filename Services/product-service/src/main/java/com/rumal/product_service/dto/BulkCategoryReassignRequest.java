package com.rumal.product_service.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record BulkCategoryReassignRequest(
        @NotEmpty(message = "productIds must not be empty")
        @Size(max = 50, message = "Cannot process more than 50 items per bulk request")
        List<UUID> productIds,

        @NotNull(message = "targetCategoryId is required")
        UUID targetCategoryId
) {}
