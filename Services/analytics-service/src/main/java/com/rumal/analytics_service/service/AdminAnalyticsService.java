package com.rumal.analytics_service.service;

import com.rumal.analytics_service.client.*;
import com.rumal.analytics_service.client.dto.*;
import com.rumal.analytics_service.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAnalyticsService {

    private final OrderAnalyticsClient orderClient;
    private final CustomerAnalyticsClient customerClient;
    private final ProductAnalyticsClient productClient;
    private final VendorAnalyticsClient vendorClient;
    private final PaymentAnalyticsClient paymentClient;
    private final InventoryAnalyticsClient inventoryClient;
    private final PromotionAnalyticsClient promotionClient;
    private final ReviewAnalyticsClient reviewClient;
    private final WishlistAnalyticsClient wishlistClient;
    private final CartAnalyticsClient cartClient;

    @Cacheable(cacheNames = "dashboardSummary")
    public AdminDashboardAnalytics getDashboardSummary(int periodDays) {
        var ordersFuture = CompletableFuture.supplyAsync(() ->
                safeCall(() -> orderClient.getPlatformSummary(periodDays), null));
        var customersFuture = CompletableFuture.supplyAsync(() ->
                safeCall(() -> customerClient.getPlatformSummary(), null));
        var productsFuture = CompletableFuture.supplyAsync(() ->
                safeCall(() -> productClient.getPlatformSummary(), null));
        var vendorsFuture = CompletableFuture.supplyAsync(() ->
                safeCall(() -> vendorClient.getPlatformSummary(), null));
        var paymentsFuture = CompletableFuture.supplyAsync(() ->
                safeCall(() -> paymentClient.getPlatformSummary(), null));
        var inventoryFuture = CompletableFuture.supplyAsync(() ->
                safeCall(() -> inventoryClient.getPlatformHealth(), null));
        var promotionsFuture = CompletableFuture.supplyAsync(() ->
                safeCall(() -> promotionClient.getPlatformSummary(), null));
        var reviewsFuture = CompletableFuture.supplyAsync(() ->
                safeCall(() -> reviewClient.getPlatformSummary(), null));
        var wishlistFuture = CompletableFuture.supplyAsync(() ->
                safeCall(() -> wishlistClient.getPlatformSummary(), null));
        var cartFuture = CompletableFuture.supplyAsync(() ->
                safeCall(() -> cartClient.getPlatformSummary(), null));
        var revenueTrendFuture = CompletableFuture.supplyAsync(() ->
                safeCall(() -> orderClient.getRevenueTrend(periodDays), List.<DailyRevenueBucket>of()));

        CompletableFuture.allOf(ordersFuture, customersFuture, productsFuture, vendorsFuture,
                paymentsFuture, inventoryFuture, promotionsFuture, reviewsFuture,
                wishlistFuture, cartFuture, revenueTrendFuture).join();

        return new AdminDashboardAnalytics(
                ordersFuture.join(), customersFuture.join(), productsFuture.join(),
                vendorsFuture.join(), paymentsFuture.join(), inventoryFuture.join(),
                promotionsFuture.join(), reviewsFuture.join(), wishlistFuture.join(),
                cartFuture.join(), revenueTrendFuture.join()
        );
    }

    @Cacheable(cacheNames = "revenueSummary")
    public AdminRevenueTrendResponse getRevenueTrend(int days) {
        var trendFuture = CompletableFuture.supplyAsync(() ->
                safeCall(() -> orderClient.getRevenueTrend(days), List.<DailyRevenueBucket>of()));
        var statusFuture = CompletableFuture.supplyAsync(() ->
                safeCall(() -> orderClient.getStatusBreakdown(), Map.<String, Long>of()));
        CompletableFuture.allOf(trendFuture, statusFuture).join();
        return new AdminRevenueTrendResponse(trendFuture.join(), statusFuture.join());
    }

    @Cacheable(cacheNames = "topProducts")
    public AdminTopProductsResponse getTopProducts() {
        var byRevenueFuture = CompletableFuture.supplyAsync(() ->
                safeCall(() -> orderClient.getTopProducts(20), List.<TopProductEntry>of()));
        var byViewsFuture = CompletableFuture.supplyAsync(() ->
                safeCall(() -> productClient.getTopViewed(20), List.<ProductViewEntry>of()));
        var bySoldFuture = CompletableFuture.supplyAsync(() ->
                safeCall(() -> productClient.getTopSold(20), List.<ProductSoldEntry>of()));
        var byWishlistedFuture = CompletableFuture.supplyAsync(() ->
                safeCall(() -> wishlistClient.getMostWished(20), List.<MostWishedProduct>of()));
        CompletableFuture.allOf(byRevenueFuture, byViewsFuture, bySoldFuture, byWishlistedFuture).join();
        return new AdminTopProductsResponse(
                byRevenueFuture.join(), byViewsFuture.join(),
                bySoldFuture.join(), byWishlistedFuture.join());
    }

    @Cacheable(cacheNames = "customerSegmentation")
    public AdminCustomerSegmentationResponse getCustomerSegmentation() {
        var summaryFuture = CompletableFuture.supplyAsync(() ->
                safeCall(() -> customerClient.getPlatformSummary(), null));
        var growthFuture = CompletableFuture.supplyAsync(() ->
                safeCall(() -> customerClient.getGrowthTrend(12), List.<MonthlyGrowthBucket>of()));
        CompletableFuture.allOf(summaryFuture, growthFuture).join();
        return new AdminCustomerSegmentationResponse(summaryFuture.join(), growthFuture.join());
    }

    @Cacheable(cacheNames = "vendorLeaderboard")
    public AdminVendorLeaderboardResponse getVendorLeaderboard(String sortBy) {
        var summaryFuture = CompletableFuture.supplyAsync(() ->
                safeCall(() -> vendorClient.getPlatformSummary(), null));
        var leaderboardFuture = CompletableFuture.supplyAsync(() ->
                safeCall(() -> vendorClient.getLeaderboard(sortBy, 20), List.<VendorLeaderboardEntry>of()));
        CompletableFuture.allOf(summaryFuture, leaderboardFuture).join();
        return new AdminVendorLeaderboardResponse(summaryFuture.join(), leaderboardFuture.join());
    }

    @Cacheable(cacheNames = "inventoryHealth")
    public AdminInventoryHealthResponse getInventoryHealth() {
        var healthFuture = CompletableFuture.supplyAsync(() ->
                safeCall(() -> inventoryClient.getPlatformHealth(), null));
        var alertsFuture = CompletableFuture.supplyAsync(() ->
                safeCall(() -> inventoryClient.getLowStockAlerts(50), List.<LowStockAlert>of()));
        CompletableFuture.allOf(healthFuture, alertsFuture).join();
        return new AdminInventoryHealthResponse(healthFuture.join(), alertsFuture.join());
    }

    @Cacheable(cacheNames = "promotionRoi")
    public AdminPromotionRoiResponse getPromotionRoi() {
        var summaryFuture = CompletableFuture.supplyAsync(() ->
                safeCall(() -> promotionClient.getPlatformSummary(), null));
        var roiFuture = CompletableFuture.supplyAsync(() ->
                safeCall(() -> promotionClient.getPromotionRoi(20), List.<PromotionRoiEntry>of()));
        CompletableFuture.allOf(summaryFuture, roiFuture).join();
        return new AdminPromotionRoiResponse(summaryFuture.join(), roiFuture.join());
    }

    @Cacheable(cacheNames = "reviewAnalytics")
    public AdminReviewAnalyticsResponse getReviewAnalytics() {
        var summaryFuture = CompletableFuture.supplyAsync(() ->
                safeCall(() -> reviewClient.getPlatformSummary(), null));
        var distFuture = CompletableFuture.supplyAsync(() ->
                safeCall(() -> reviewClient.getRatingDistribution(), Map.<Integer, Long>of()));
        CompletableFuture.allOf(summaryFuture, distFuture).join();
        return new AdminReviewAnalyticsResponse(summaryFuture.join(), distFuture.join());
    }

    @CacheEvict(cacheNames = {"dashboardSummary", "revenueSummary", "topProducts", "vendorLeaderboard",
            "customerSegmentation", "inventoryHealth", "promotionRoi", "reviewAnalytics",
            "vendorAnalytics", "customerInsights"}, allEntries = true)
    public void evictAllCaches() {
        log.info("All analytics caches evicted");
    }

    public DashboardSummaryResponse getLegacyDashboardSummary() {
        var orderSummary = safeCall(() -> orderClient.getPlatformSummary(30), null);
        var vendorSummary = safeCall(() -> vendorClient.getPlatformSummary(), null);
        var productSummary = safeCall(() -> productClient.getPlatformSummary(), null);
        var promotionSummary = safeCall(() -> promotionClient.getPlatformSummary(), null);

        return buildLegacyDashboard(orderSummary, vendorSummary, productSummary, promotionSummary);
    }

    private DashboardSummaryResponse buildLegacyDashboard(
            PlatformOrderSummary orders,
            VendorPlatformSummary vendors,
            ProductPlatformSummary products,
            PromotionPlatformSummary promotions) {

        long totalOrders = 0;
        long pendingOrders = 0;
        long processingOrders = 0;
        long completedOrders = 0;
        long cancelledOrders = 0;
        long totalVendors = 0;
        long activeVendors = 0;
        long totalProducts = 0;
        long activePromotions = 0;
        Map<String, Long> ordersByStatus = new LinkedHashMap<>();

        if (orders != null) {
            totalOrders = orders.totalOrders();
            pendingOrders = orders.pendingOrders();
            processingOrders = orders.processingOrders() + orders.shippedOrders();
            completedOrders = orders.deliveredOrders();
            cancelledOrders = orders.cancelledOrders();

            ordersByStatus.put("PAYMENT_PENDING", orders.pendingOrders());
            ordersByStatus.put("PROCESSING", orders.processingOrders());
            ordersByStatus.put("SHIPPED", orders.shippedOrders());
            ordersByStatus.put("DELIVERED", orders.deliveredOrders());
            ordersByStatus.put("CANCELLED", orders.cancelledOrders());
            ordersByStatus.put("REFUNDED", orders.refundedOrders());
        }

        if (vendors != null) {
            totalVendors = vendors.totalVendors();
            activeVendors = vendors.activeVendors();
        }

        if (products != null) {
            totalProducts = products.totalProducts();
        }

        if (promotions != null) {
            activePromotions = promotions.activeCampaigns();
        }

        return new DashboardSummaryResponse(
                totalOrders, pendingOrders, processingOrders, completedOrders, cancelledOrders,
                totalVendors, activeVendors, totalProducts, activePromotions,
                ordersByStatus, Instant.now()
        );
    }

    private <T> T safeCall(java.util.function.Supplier<T> action, T fallback) {
        try {
            return action.get();
        } catch (Exception e) {
            log.warn("Analytics downstream call failed: {}", e.getMessage());
            return fallback;
        }
    }
}
