package com.rumal.analytics_service.dto;

import com.rumal.analytics_service.client.dto.DailyRevenueBucket;
import java.util.List;
import java.util.Map;

public record AdminRevenueTrendResponse(
    List<DailyRevenueBucket> trend,
    Map<String, Long> statusBreakdown
) {}
