package com.rumal.wishlist_service.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WishlistCollectionResponse(
        UUID id,
        String name,
        String description,
        boolean isDefault,
        boolean shared,
        String shareToken,
        List<WishlistItemResponse> items,
        int itemCount,
        Instant createdAt,
        Instant updatedAt
) {
}
