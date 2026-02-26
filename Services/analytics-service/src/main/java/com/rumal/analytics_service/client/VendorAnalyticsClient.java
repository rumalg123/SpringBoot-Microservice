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
import java.util.UUID;
import java.util.function.Supplier;

@Component
public class VendorAnalyticsClient {

    private static final String BASE_URL = "http://vendor-service/internal/vendors/analytics";

    private final RestClient restClient;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;
    private final RetryRegistry retryRegistry;
    private final String internalAuth;


    public VendorAnalyticsClient(
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

    public VendorPlatformSummary getPlatformSummary() {
        return call(() -> get(BASE_URL + "/platform/summary", VendorPlatformSummary.class));
    }

    public List<VendorLeaderboardEntry> getLeaderboard(String sortBy, int limit) {
        return call(() -> getList(BASE_URL + "/platform/leaderboard?sortBy=" + sortBy + "&limit=" + limit, new ParameterizedTypeReference<>() {}));
    }

    public VendorPerformanceSummary getVendorPerformance(UUID vendorId) {
        return call(() -> get(BASE_URL + "/" + vendorId + "/performance", VendorPerformanceSummary.class));
    }

    private <T> T get(String url, Class<T> type) {
        try {
            return restClient.get()
                    .uri(url)
                    .header("X-Internal-Auth", internalAuth)
                    .retrieve()
                    .body(type);
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Vendor analytics service unavailable", ex);
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
            throw new ServiceUnavailableException("Vendor analytics service unavailable", ex);
        }
    }

    private <T> T call(Supplier<T> action) {
        var retry = retryRegistry.retry("analytics-vendor-client");
        return Retry.decorateSupplier(retry, () ->
                circuitBreakerFactory.create("analytics-vendor-client")
                        .run(action::get, throwable -> {
                            if (throwable instanceof RuntimeException re) throw re;
                            throw new ServiceUnavailableException("Vendor analytics service unavailable", throwable);
                        })
        ).get();
    }
}
