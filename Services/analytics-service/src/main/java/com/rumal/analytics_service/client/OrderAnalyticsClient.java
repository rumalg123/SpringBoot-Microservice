package com.rumal.analytics_service.client;

import com.rumal.analytics_service.client.dto.*;
import com.rumal.analytics_service.exception.ServiceUnavailableException;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@Component
public class OrderAnalyticsClient {

    private final RestClient restClient;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;
    private final RetryRegistry retryRegistry;
    private final String internalAuth;

    public OrderAnalyticsClient(
            @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lbRestClientBuilder,
            CircuitBreakerFactory<?, ?> circuitBreakerFactory,
            RetryRegistry retryRegistry,
            @Value("${internal.auth.shared-secret:}") String internalAuth
    ) {
        this.restClient = lbRestClientBuilder.build();
        this.circuitBreakerFactory = circuitBreakerFactory;
        this.retryRegistry = retryRegistry;
        this.internalAuth = internalAuth;
    }

    public PlatformOrderSummary getPlatformSummary(int periodDays) {
        return call(() -> get("http://order-service/internal/orders/analytics/platform/summary?periodDays=" + periodDays, PlatformOrderSummary.class));
    }

    public List<DailyRevenueBucket> getRevenueTrend(int days) {
        return call(() -> getList("http://order-service/internal/orders/analytics/platform/revenue-trend?days=" + days, new ParameterizedTypeReference<>() {}));
    }

    public List<TopProductEntry> getTopProducts(int limit) {
        return call(() -> getList("http://order-service/internal/orders/analytics/platform/top-products?limit=" + limit, new ParameterizedTypeReference<>() {}));
    }

    public Map<String, Long> getStatusBreakdown() {
        return call(() -> get("http://order-service/internal/orders/analytics/platform/status-breakdown", new ParameterizedTypeReference<Map<String, Long>>() {}));
    }

    public VendorOrderSummary getVendorSummary(UUID vendorId, int periodDays) {
        return call(() -> get("http://order-service/internal/orders/analytics/vendors/" + vendorId + "/summary?periodDays=" + periodDays, VendorOrderSummary.class));
    }

    public List<DailyRevenueBucket> getVendorRevenueTrend(UUID vendorId, int days) {
        return call(() -> getList("http://order-service/internal/orders/analytics/vendors/" + vendorId + "/revenue-trend?days=" + days, new ParameterizedTypeReference<>() {}));
    }

    public List<TopProductEntry> getVendorTopProducts(UUID vendorId, int limit) {
        return call(() -> getList("http://order-service/internal/orders/analytics/vendors/" + vendorId + "/top-products?limit=" + limit, new ParameterizedTypeReference<>() {}));
    }

    public CustomerOrderSummary getCustomerSummary(UUID customerId) {
        return call(() -> get("http://order-service/internal/orders/analytics/customers/" + customerId + "/summary", CustomerOrderSummary.class));
    }

    public List<MonthlySpendBucket> getCustomerSpendingTrend(UUID customerId, int months) {
        return call(() -> getList("http://order-service/internal/orders/analytics/customers/" + customerId + "/spending-trend?months=" + months, new ParameterizedTypeReference<>() {}));
    }

    private <T> T get(String url, Class<T> type) {
        try {
            return restClient.get()
                    .uri(url)
                    .header("X-Internal-Auth", internalAuth)
                    .retrieve()
                    .body(type);
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Order analytics service unavailable", ex);
        }
    }

    private <T> T get(String url, ParameterizedTypeReference<T> type) {
        try {
            return restClient.get()
                    .uri(url)
                    .header("X-Internal-Auth", internalAuth)
                    .retrieve()
                    .body(type);
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Order analytics service unavailable", ex);
        }
    }

    private <T> List<T> getList(String url, ParameterizedTypeReference<List<T>> type) {
        try {
            List<T> result = restClient.get()
                    .uri(url)
                    .header("X-Internal-Auth", internalAuth)
                    .retrieve()
                    .body(type);
            return result == null ? List.of() : result;
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Order analytics service unavailable", ex);
        }
    }

    private <T> T call(Supplier<T> action) {
        var retry = retryRegistry.retry("analytics-order-client");
        return Retry.decorateSupplier(retry, () ->
                circuitBreakerFactory.create("analytics-order-client")
                        .run(action::get, throwable -> {
                            if (throwable instanceof RuntimeException re) throw re;
                            throw new ServiceUnavailableException("Order analytics service unavailable", throwable);
                        })
        ).get();
    }
}
