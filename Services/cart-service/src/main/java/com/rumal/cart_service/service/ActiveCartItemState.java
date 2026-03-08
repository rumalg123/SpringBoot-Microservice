package com.rumal.cart_service.service;

import java.util.UUID;

public record ActiveCartItemState(
        UUID id,
        UUID productId,
        int quantity,
        boolean savedForLater
) {
}
