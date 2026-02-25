package com.rumal.analytics_service.dto;

import com.rumal.analytics_service.client.dto.*;
import java.util.List;

public record AdminPromotionRoiResponse(
    PromotionPlatformSummary summary,
    List<PromotionRoiEntry> campaigns
) {}
