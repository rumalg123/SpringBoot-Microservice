package com.rumal.analytics_service.service;

import com.rumal.analytics_service.client.CustomerAnalyticsClient;
import com.rumal.analytics_service.client.OrderAnalyticsClient;
import com.rumal.analytics_service.client.dto.*;
import com.rumal.analytics_service.dto.CustomerInsightsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerAnalyticsService {

    private final OrderAnalyticsClient orderClient;
    private final CustomerAnalyticsClient customerClient;

    @Cacheable(cacheNames = "customerInsights", key = "#customerId")
    public CustomerInsightsResponse getCustomerInsights(UUID customerId) {
        var orderSummaryFuture = CompletableFuture.supplyAsync(() -> safeCall(() -> orderClient.getCustomerSummary(customerId), null));
        var spendingTrendFuture = CompletableFuture.supplyAsync(() -> safeCall(() -> orderClient.getCustomerSpendingTrend(customerId, 12), List.<MonthlySpendBucket>of()));
        var profileFuture = CompletableFuture.supplyAsync(() -> safeCall(() -> customerClient.getProfileSummary(customerId), null));

        CompletableFuture.allOf(orderSummaryFuture, spendingTrendFuture, profileFuture).join();

        return new CustomerInsightsResponse(
            orderSummaryFuture.join(), spendingTrendFuture.join(), profileFuture.join()
        );
    }

    private <T> T safeCall(java.util.function.Supplier<T> action, T fallback) {
        try {
            return action.get();
        } catch (Exception e) {
            log.warn("Customer analytics downstream call failed: {}", e.getMessage());
            return fallback;
        }
    }
}
