package com.rumal.promotion_service.dto.analytics;

import java.math.BigDecimal;
import java.util.UUID;

public record VendorPromotionSummary(
    UUID vendorId,
    long totalCampaigns,
    long activeCampaigns,
    BigDecimal totalBudget,
    BigDecimal totalBurned
) {}
