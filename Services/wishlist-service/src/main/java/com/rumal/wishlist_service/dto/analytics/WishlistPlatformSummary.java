package com.rumal.wishlist_service.dto.analytics;

public record WishlistPlatformSummary(
    long totalWishlistItems,
    long uniqueCustomers,
    long uniqueProducts
) {}
