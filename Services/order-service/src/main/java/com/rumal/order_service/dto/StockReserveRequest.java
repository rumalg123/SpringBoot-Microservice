package com.rumal.order_service.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StockReserveRequest(
        UUID orderId,
        List<StockCheckRequest> items,
        Instant expiresAt
) {}
