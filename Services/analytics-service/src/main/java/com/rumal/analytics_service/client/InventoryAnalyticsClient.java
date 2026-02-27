package com.rumal.analytics_service.client;

import com.rumal.analytics_service.client.dto.*;
import com.rumal.analytics_service.exception.DownstreamHttpException;
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
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@Component
public class InventoryAnalyticsClient {

    private static final String BASE_URL = "http://inventory-service/internal/inventory/analytics";

    private final RestClient restClient;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;
    private final RetryRegistry retryRegistry;
    private final String internalAuth;

    public InventoryAnalyticsClient(
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

    public InventoryHealthSummary getPlatformHealth() {
        return call(() -> get(BASE_URL + "/platform/health", InventoryHealthSummary.class));
    }

    public List<LowStockAlert> getLowStockAlerts(int limit) {
        return call(() -> getList(BASE_URL + "/platform/low-stock-alerts?limit=" + limit, new ParameterizedTypeReference<>() {}));
    }

    public VendorInventoryHealth getVendorHealth(UUID vendorId) {
        return call(() -> get(BASE_URL + "/vendors/" + vendorId + "/health", VendorInventoryHealth.class));
    }

    private <T> T get(String url, Class<T> type) {
        try {
            return restClient.get()
                    .uri(url)
                    .header("X-Internal-Auth", internalAuth)
                    .retrieve()
                    .body(type);
        } catch (RestClientResponseException ex) {
            throw new DownstreamHttpException(ex.getStatusCode(), "Inventory analytics HTTP error: " + ex.getStatusCode().value(), ex);
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Inventory analytics service unavailable", ex);
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
        } catch (RestClientResponseException ex) {
            throw new DownstreamHttpException(ex.getStatusCode(), "Inventory analytics HTTP error: " + ex.getStatusCode().value(), ex);
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Inventory analytics service unavailable", ex);
        }
    }

    private <T> T call(Supplier<T> action) {
        var retry = retryRegistry.retry("analytics-inventory-client");
        return Retry.decorateSupplier(retry, () ->
                circuitBreakerFactory.create("analytics-inventory-client")
                        .run(action::get, throwable -> {
                            if (throwable instanceof RuntimeException re) throw re;
                            throw new ServiceUnavailableException("Inventory analytics service unavailable", throwable);
                        })
        ).get();
    }
}
