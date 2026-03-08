package com.rumal.order_service.scheduler;

import com.rumal.order_service.service.OrderAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderAnalyticsCacheWarmupScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderAnalyticsCacheWarmupScheduler.class);

    private final OrderAnalyticsService orderAnalyticsService;

    @Scheduled(
            initialDelayString = "${order.analytics.cache-warmup.initial-delay-ms:5000}",
            fixedDelayString = "${order.analytics.cache-warmup.interval-ms:300000}"
    )
    public void warmPlatformAnalytics() {
        try {
            orderAnalyticsService.getPlatformSummary(30);
            orderAnalyticsService.getRevenueTrend(7);
            orderAnalyticsService.getRevenueTrend(30);
            orderAnalyticsService.getRevenueTrend(90);
            orderAnalyticsService.getTopProducts(20);
            orderAnalyticsService.getStatusBreakdown();
        } catch (Exception ex) {
            log.warn("Order analytics cache warmup failed", ex);
        }
    }
}
