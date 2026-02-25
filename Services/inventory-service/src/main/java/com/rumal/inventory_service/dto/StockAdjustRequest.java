package com.rumal.inventory_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StockAdjustRequest(
        int quantityChange,
        @NotBlank @Size(max = 500) String reason
) {}
