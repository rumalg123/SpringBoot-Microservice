package com.rumal.wishlist_service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SharedWishlistItemResponse(
        UUID productId,
        String productSlug,
        String productName,
        String productType,
        String mainImage,
        BigDecimal sellingPriceSnapshot,
        Instant createdAt
) {}
