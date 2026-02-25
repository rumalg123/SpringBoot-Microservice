package com.rumal.inventory_service.dto;

import jakarta.validation.constraints.NotNull;

public record WarehouseStatusRequest(
        @NotNull Boolean active
) {}
