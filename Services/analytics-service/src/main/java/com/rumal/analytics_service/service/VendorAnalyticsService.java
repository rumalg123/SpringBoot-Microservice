package com.rumal.analytics_service.service;

import com.rumal.analytics_service.client.*;
import com.rumal.analytics_service.client.dto.*;
import com.rumal.analytics_service.dto.VendorDashboardAnalytics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Slf4j
@Service
@RequiredArgsConstructor
public class VendorAnalyticsService {

    private final OrderAnalyticsClient orderClient;
    private final ProductAnalyticsClient productClient;
    private final InventoryAnalyticsClient inventoryClient;
    private final PromotionAnalyticsClient promotionClient;
    private final ReviewAnalyticsClient reviewClient;
    private final VendorAnalyticsClient vendorClient;
    private final ExecutorService analyticsExecutor;

    @Cacheable(cacheNames = "vendorAnalytics", key = "#vendorId")
    public VendorDashboardAnalytics getVendorDashboard(UUID vendorId) {
        var ordersFuture = CompletableFuture.supplyAsync(() -> safeCall(() -> orderClient.getVendorSummary(vendorId, 30), null), analyticsExecutor);
        var revenueTrendFuture = CompletableFuture.supplyAsync(() -> safeCall(() -> orderClient.getVendorRevenueTrend(vendorId, 30), List.<DailyRevenueBucket>of()), analyticsExecutor);
        var topProductsFuture = CompletableFuture.supplyAsync(() -> safeCall(() -> orderClient.getVendorTopProducts(vendorId, 10), List.<TopProductEntry>of()), analyticsExecutor);
        var productsFuture = CompletableFuture.supplyAsync(() -> safeCall(() -> productClient.getVendorSummary(vendorId), null), analyticsExecutor);
        var inventoryFuture = CompletableFuture.supplyAsync(() -> safeCall(() -> inventoryClient.getVendorHealth(vendorId), null), analyticsExecutor);
        var promotionsFuture = CompletableFuture.supplyAsync(() -> safeCall(() -> promotionClient.getVendorSummary(vendorId), null), analyticsExecutor);
        var reviewsFuture = CompletableFuture.supplyAsync(() -> safeCall(() -> reviewClient.getVendorSummary(vendorId), null), analyticsExecutor);
        var performanceFuture = CompletableFuture.supplyAsync(() -> safeCall(() -> vendorClient.getVendorPerformance(vendorId), null), analyticsExecutor);

        CompletableFuture.allOf(ordersFuture, revenueTrendFuture, topProductsFuture,
            productsFuture, inventoryFuture, promotionsFuture, reviewsFuture, performanceFuture).join();

        return new VendorDashboardAnalytics(
            ordersFuture.join(), revenueTrendFuture.join(), topProductsFuture.join(),
            productsFuture.join(), inventoryFuture.join(), promotionsFuture.join(),
            reviewsFuture.join(), performanceFuture.join()
        );
    }

    private <T> T safeCall(java.util.function.Supplier<T> action, T fallback) {
        try {
            return action.get();
        } catch (Exception e) {
            log.warn("Vendor analytics downstream call failed: {}", e.getMessage(), e);
            return fallback;
        }
    }
}
