package com.rumal.analytics_service.client;

import com.rumal.analytics_service.client.dto.*;
import com.rumal.analytics_service.exception.ServiceUnavailableException;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.function.Supplier;

@Component
public class CartAnalyticsClient {

    private static final String BASE_URL = "http://cart-service/internal/carts/analytics";

    private final RestClient.Builder lbRestClientBuilder;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;
    private final RetryRegistry retryRegistry;
    private final String internalAuth;

    public CartAnalyticsClient(
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

    public CartPlatformSummary getPlatformSummary() {
        return call(() -> get(BASE_URL + "/platform/summary", CartPlatformSummary.class));
    }

    private <T> T get(String url, Class<T> type) {
        try {
            return lbRestClientBuilder.build().get()
                    .uri(url)
                    .header("X-Internal-Auth", internalAuth)
                    .retrieve()
                    .body(type);
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Cart analytics service unavailable", ex);
        }
    }

    private <T> T call(Supplier<T> action) {
        var retry = retryRegistry.retry("analytics-cart-client");
        return Retry.decorateSupplier(retry, () ->
                circuitBreakerFactory.create("analytics-cart-client")
                        .run(action::get, throwable -> {
                            if (throwable instanceof RuntimeException re) throw re;
                            throw new ServiceUnavailableException("Cart analytics service unavailable", throwable);
                        })
        ).get();
    }
}
