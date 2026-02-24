package com.rumal.wishlist_service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WishlistItemResponse(
        UUID id,
        UUID collectionId,
        UUID productId,
        String productSlug,
        String productName,
        String productType,
        String mainImage,
        BigDecimal sellingPriceSnapshot,
        String note,
        Instant createdAt,
        Instant updatedAt
) {
}
