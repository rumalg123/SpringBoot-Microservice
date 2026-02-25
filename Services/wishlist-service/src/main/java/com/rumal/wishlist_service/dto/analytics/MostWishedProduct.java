package com.rumal.wishlist_service.dto.analytics;

import java.util.UUID;

public record MostWishedProduct(UUID productId, String productName, long wishlistCount) {}
