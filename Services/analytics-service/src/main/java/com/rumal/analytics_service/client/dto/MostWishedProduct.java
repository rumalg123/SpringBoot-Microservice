package com.rumal.analytics_service.client.dto;

import java.util.UUID;

public record MostWishedProduct(
        UUID productId,
        String productName,
        long wishlistCount
) {}
