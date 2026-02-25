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
public class CustomerAnalyticsClient {

    private static final String BASE_URL = "http://customer-service/internal/customers/analytics";

    private final RestClient.Builder lbRestClientBuilder;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;
    private final RetryRegistry retryRegistry;
    private final String internalAuth;

    public CustomerAnalyticsClient(
            @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lbRestClientBuilder,
            CircuitBreakerFactory<?, ?> circuitBreakerFactory,
            RetryRegistry retryRegistry,
            @Value("${internal.auth.shared-secret:}") String internalAuth
    ) {
        this.lbRestClientBuilder = lbRestClientBuilder;
        this.circuitBreakerFactory = circuitBreakerFactory;
        this.retryRegistry = retryRegistry;
        this.internalAuth = internalAuth;
    }

    public CustomerPlatformSummary getPlatformSummary() {
        return call(() -> get(BASE_URL + "/platform/summary", CustomerPlatformSummary.class));
    }

    public List<MonthlyGrowthBucket> getGrowthTrend(int months) {
        return call(() -> getList(BASE_URL + "/platform/growth-trend?months=" + months, new ParameterizedTypeReference<>() {}));
    }

    public CustomerProfileSummary getProfileSummary(UUID customerId) {
        return call(() -> get(BASE_URL + "/customers/" + customerId + "/profile-summary", CustomerProfileSummary.class));
    }

    private <T> T get(String url, Class<T> type) {
        try {
            return lbRestClientBuilder.build().get()
                    .uri(url)
                    .header("X-Internal-Auth", internalAuth)
                    .retrieve()
                    .body(type);
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Customer analytics service unavailable", ex);
        }
    }

    private <T> T get(String url, ParameterizedTypeReference<T> type) {
        try {
            return lbRestClientBuilder.build().get()
                    .uri(url)
                    .header("X-Internal-Auth", internalAuth)
                    .retrieve()
                    .body(type);
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Customer analytics service unavailable", ex);
        }
    }

    private <T> List<T> getList(String url, ParameterizedTypeReference<List<T>> type) {
        try {
            List<T> result = lbRestClientBuilder.build().get()
                    .uri(url)
                    .header("X-Internal-Auth", internalAuth)
                    .retrieve()
                    .body(type);
            return result == null ? List.of() : result;
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Customer analytics service unavailable", ex);
        }
    }

    private <T> T call(Supplier<T> action) {
        var retry = retryRegistry.retry("analytics-customer-client");
        return Retry.decorateSupplier(retry, () ->
                circuitBreakerFactory.create("analytics-customer-client")
                        .run(action::get, throwable -> {
                            if (throwable instanceof RuntimeException re) throw re;
                            throw new ServiceUnavailableException("Customer analytics service unavailable", throwable);
                        })
        ).get();
    }
}
