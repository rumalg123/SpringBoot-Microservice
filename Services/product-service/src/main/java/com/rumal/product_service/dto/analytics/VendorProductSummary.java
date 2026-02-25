package com.rumal.product_service.dto.analytics;

import java.util.UUID;

public record VendorProductSummary(
    UUID vendorId,
    long totalProducts,
    long activeProducts,
    long totalViews,
    long totalSold
) {}
