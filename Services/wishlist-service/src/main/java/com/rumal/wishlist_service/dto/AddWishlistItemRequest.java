package com.rumal.wishlist_service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AddWishlistItemRequest(
        @NotNull UUID productId,
        UUID collectionId,
        @Size(max = 500) String note
) {
}
