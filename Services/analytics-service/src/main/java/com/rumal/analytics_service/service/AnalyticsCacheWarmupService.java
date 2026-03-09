package com.rumal.analytics_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsCacheWarmupService {

    private final AdminAnalyticsService adminAnalyticsService;

    @Scheduled(
            initialDelayString = "${analytics.cache-warmup.initial-delay-ms:5000}",
            fixedDelayString = "${analytics.cache-warmup.interval-ms:300000}"
    )
    public void warmAdminDashboardCaches() {
        try {
            adminAnalyticsService.getDashboardSummary(30);
            adminAnalyticsService.getRevenueTrend(7);
            adminAnalyticsService.getRevenueTrend(30);
            adminAnalyticsService.getRevenueTrend(90);
            adminAnalyticsService.getTopProducts();
            adminAnalyticsService.getCustomerSegmentation();
            adminAnalyticsService.getVendorLeaderboard("ORDERS_COMPLETED");
            adminAnalyticsService.getInventoryHealth();
            adminAnalyticsService.getPromotionRoi();
            adminAnalyticsService.getReviewAnalytics();
        } catch (Exception ex) {
            log.warn("Analytics cache warmup failed", ex);
        }
    }
}
