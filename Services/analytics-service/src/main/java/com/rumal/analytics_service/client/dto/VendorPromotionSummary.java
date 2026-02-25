package com.rumal.analytics_service.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record VendorPromotionSummary(
        UUID vendorId,
        long totalCampaigns,
        long activeCampaigns,
        BigDecimal totalBudget,
        BigDecimal totalBurned
) {}
