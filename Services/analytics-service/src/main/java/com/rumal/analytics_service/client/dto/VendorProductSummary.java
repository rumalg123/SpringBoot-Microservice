package com.rumal.analytics_service.client.dto;

import java.util.UUID;

public record VendorProductSummary(
        UUID vendorId,
        long totalProducts,
        long activeProducts,
        long totalViews,
        long totalSold
) {}
