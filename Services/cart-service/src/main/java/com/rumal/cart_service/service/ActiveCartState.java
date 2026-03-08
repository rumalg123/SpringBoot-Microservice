package com.rumal.cart_service.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ActiveCartState(
        UUID id,
        String keycloakId,
        String note,
        List<ActiveCartItemState> items,
        Instant createdAt,
        Instant updatedAt,
        Instant lastActivityAt
) {
    public ActiveCartState {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
