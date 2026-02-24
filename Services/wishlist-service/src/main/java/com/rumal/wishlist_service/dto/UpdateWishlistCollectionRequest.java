package com.rumal.wishlist_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateWishlistCollectionRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description
) {
}
