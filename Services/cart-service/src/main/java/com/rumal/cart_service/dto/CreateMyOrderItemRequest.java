package com.rumal.cart_service.dto;

import java.util.UUID;

public record CreateMyOrderItemRequest(
        UUID productId,
        int quantity
) {
}
