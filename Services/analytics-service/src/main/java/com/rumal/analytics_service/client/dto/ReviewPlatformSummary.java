package com.rumal.analytics_service.client.dto;

public record ReviewPlatformSummary(
        long totalReviews,
        long activeReviews,
        double avgRating,
        double verifiedPurchasePercent,
        long totalReported,
        long reviewsThisMonth
) {}
