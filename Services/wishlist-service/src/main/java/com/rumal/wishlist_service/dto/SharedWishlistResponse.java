package com.rumal.wishlist_service.dto;

import java.util.List;

public record SharedWishlistResponse(
        String collectionName,
        String description,
        List<WishlistItemResponse> items,
        int itemCount
) {
}
