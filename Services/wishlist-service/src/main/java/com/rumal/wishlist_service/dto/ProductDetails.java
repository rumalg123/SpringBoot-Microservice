package com.rumal.wishlist_service.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProductDetails(
        UUID id,
        String slug,
        String name,
        String sku,
        String productType,
        boolean active,
        boolean deleted,
        BigDecimal sellingPrice,
        List<String> images
) {
}
