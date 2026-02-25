package com.rumal.promotion_service.dto.analytics;

import java.math.BigDecimal;

public record PromotionPlatformSummary(
    long totalCampaigns,
    long activeCampaigns,
    long scheduledCampaigns,
    long expiredCampaigns,
    long flashSaleCount,
    BigDecimal totalBudget,
    BigDecimal totalBurnedBudget,
    double budgetUtilizationPercent
) {}
