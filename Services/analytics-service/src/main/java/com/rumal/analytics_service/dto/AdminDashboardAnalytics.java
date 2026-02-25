package com.rumal.analytics_service.dto;

import com.rumal.analytics_service.client.dto.*;
import java.util.List;

public record AdminDashboardAnalytics(
    PlatformOrderSummary orders,
    CustomerPlatformSummary customers,
    ProductPlatformSummary products,
    VendorPlatformSummary vendors,
    PaymentPlatformSummary payments,
    InventoryHealthSummary inventory,
    PromotionPlatformSummary promotions,
    ReviewPlatformSummary reviews,
    WishlistPlatformSummary wishlist,
    CartPlatformSummary cart,
    List<DailyRevenueBucket> revenueTrend
) {}
