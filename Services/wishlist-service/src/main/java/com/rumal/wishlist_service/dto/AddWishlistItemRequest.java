package com.rumal.wishlist_service.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddWishlistItemRequest(
        @NotNull UUID productId
) {
}
