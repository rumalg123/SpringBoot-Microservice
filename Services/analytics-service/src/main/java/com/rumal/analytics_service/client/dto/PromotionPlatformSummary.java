package com.rumal.analytics_service.client.dto;

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
