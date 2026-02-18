package com.rumal.order_service.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateMyOrderRequest(
        @NotBlank String item,
        @Min(1) int quantity
) {}
