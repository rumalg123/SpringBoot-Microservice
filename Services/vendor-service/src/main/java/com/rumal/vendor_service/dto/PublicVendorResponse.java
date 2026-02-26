package com.rumal.vendor_service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record PublicVendorResponse(
        UUID id,
        String name,
        String slug,
        String supportEmail,
        String logoImage,
        String bannerImage,
        String websiteUrl,
        String description,
        boolean acceptingOrders,
        // Metrics (public-safe)
        BigDecimal averageRating,
        int totalRatings,
        int totalOrdersCompleted,
        // Policies
        String returnPolicy,
        String shippingPolicy,
        int processingTimeDays,
        boolean acceptsReturns,
        int returnWindowDays,
        BigDecimal freeShippingThreshold,
        // Categories
        String primaryCategory,
        Set<String> specializations,
        Instant createdAt
) {
}
