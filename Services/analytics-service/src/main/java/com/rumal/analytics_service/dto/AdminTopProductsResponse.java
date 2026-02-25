package com.rumal.analytics_service.dto;

import com.rumal.analytics_service.client.dto.*;
import java.util.List;

public record AdminTopProductsResponse(
    List<TopProductEntry> byRevenue,
    List<ProductViewEntry> byViews,
    List<ProductSoldEntry> bySold,
    List<MostWishedProduct> byWishlisted
) {}
