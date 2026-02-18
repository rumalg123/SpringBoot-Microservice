package com.rumal.order_service.dto;

import java.util.UUID;

public record OrderItemResponse(
        UUID id,
        String item,
        int quantity
) {}
