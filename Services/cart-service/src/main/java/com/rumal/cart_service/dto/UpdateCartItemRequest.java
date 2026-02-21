package com.rumal.cart_service.dto;

import jakarta.validation.constraints.Min;

public record UpdateCartItemRequest(
        @Min(1) int quantity
) {
}
