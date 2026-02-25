package com.rumal.review_service.dto.analytics;

import java.util.Map;
import java.util.UUID;

public record VendorReviewSummary(
    UUID vendorId,
    long totalReviews,
    double avgRating,
    Map<Integer, Long> ratingDistribution,
    double verifiedPurchasePercent,
    double replyRate
) {}
