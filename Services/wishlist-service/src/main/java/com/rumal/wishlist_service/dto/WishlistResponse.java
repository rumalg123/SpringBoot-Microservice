package com.rumal.wishlist_service.dto;

import java.util.List;

public record WishlistResponse(
        String keycloakId,
        List<WishlistItemResponse> items,
        int itemCount
) {
}
