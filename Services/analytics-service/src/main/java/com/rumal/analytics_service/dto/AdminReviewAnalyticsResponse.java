package com.rumal.analytics_service.dto;

import com.rumal.analytics_service.client.dto.ReviewPlatformSummary;
import java.util.Map;

public record AdminReviewAnalyticsResponse(
    ReviewPlatformSummary summary,
    Map<Integer, Long> ratingDistribution
) {}
