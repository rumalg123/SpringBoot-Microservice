package com.rumal.personalization_service.client;

import com.rumal.personalization_service.client.dto.BatchProductRequest;
import com.rumal.personalization_service.client.dto.ProductSummary;
import com.rumal.personalization_service.exception.ServiceUnavailableException;
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
public class ProductClient {

    private static final String BASE_URL = "http://product-service/internal/products/personalization";

    private final RestClient restClient;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;
    private final RetryRegistry retryRegistry;
    private final String internalAuth;

    public ProductClient(
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

    public List<ProductSummary> getBatchSummaries(List<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) return List.of();
        return call(() -> {
            try {
                List<ProductSummary> result = restClient.post()
                        .uri(BASE_URL + "/batch-summaries")
                        .header("X-Internal-Auth", internalAuth)
                        .body(new BatchProductRequest(productIds))
                        .retrieve()
                        .body(new ParameterizedTypeReference<List<ProductSummary>>() {});
                return result == null ? List.of() : result;
            } catch (RestClientException ex) {
                throw new ServiceUnavailableException("Product service unavailable", ex);
            }
        });
    }

    private <T> T call(Supplier<T> action) {
        var retry = retryRegistry.retry("person-product-client");
        return Retry.decorateSupplier(retry, () ->
                circuitBreakerFactory.create("person-product-client")
                        .run(action::get, throwable -> {
                            if (throwable instanceof RuntimeException re) throw re;
                            throw new ServiceUnavailableException("Product service unavailable", throwable);
                        })
        ).get();
    }
}
