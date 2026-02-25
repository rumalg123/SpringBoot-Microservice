package com.rumal.analytics_service.client.dto;

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
