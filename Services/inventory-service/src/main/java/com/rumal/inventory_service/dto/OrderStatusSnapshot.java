package com.rumal.inventory_service.dto;

import java.util.UUID;

public record OrderStatusSnapshot(
        UUID id,
        String status
) {
}
