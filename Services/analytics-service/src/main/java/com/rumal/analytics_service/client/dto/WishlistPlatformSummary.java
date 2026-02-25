package com.rumal.analytics_service.client.dto;

public record WishlistPlatformSummary(
        long totalWishlistItems,
        long uniqueCustomers,
        long uniqueProducts
) {}
