package com.rumal.analytics_service.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PromotionRoiEntry(
        UUID campaignId,
        String name,
        UUID vendorId,
        BigDecimal budgetAmount,
        BigDecimal burnedBudgetAmount,
        double utilizationPercent,
        String benefitType,
        boolean isFlashSale
) {}
