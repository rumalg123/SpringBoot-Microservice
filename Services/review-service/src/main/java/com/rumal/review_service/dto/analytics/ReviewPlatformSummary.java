package com.rumal.review_service.dto.analytics;

public record ReviewPlatformSummary(
    long totalReviews,
    long activeReviews,
    double avgRating,
    double verifiedPurchasePercent,
    long totalReported,
    long reviewsThisMonth
) {}
