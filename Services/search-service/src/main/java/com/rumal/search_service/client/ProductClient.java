package com.rumal.search_service.client;

import com.rumal.search_service.client.dto.ProductIndexPage;
import com.rumal.search_service.exception.ServiceUnavailableException;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.function.Supplier;

@Component
public class ProductClient {

    private static final String BASE_URL = "http://product-service/internal/products/search";

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

    public ProductIndexPage fetchCatalogPage(int page, int size) {
        return call(() -> {
            try {
                return restClient.get()
                        .uri(BASE_URL + "/catalog?page={page}&size={size}", page, size)
                        .header("X-Internal-Auth", internalAuth)
                        .retrieve()
                        .body(ProductIndexPage.class);
            } catch (RestClientException ex) {
                throw new ServiceUnavailableException("Product service unavailable", ex);
            }
        });
    }

    public ProductIndexPage fetchUpdatedSince(Instant since, int page, int size) {
        return call(() -> {
            try {
                return restClient.get()
                        .uri(BASE_URL + "/updated-since?since={since}&page={page}&size={size}",
                                since.toString(), page, size)
                        .header("X-Internal-Auth", internalAuth)
                        .retrieve()
                        .body(ProductIndexPage.class);
            } catch (RestClientException ex) {
                throw new ServiceUnavailableException("Product service unavailable", ex);
            }
        });
    }

    private <T> T call(Supplier<T> action) {
        var retry = retryRegistry.retry("search-product-client");
        return Retry.decorateSupplier(retry, () ->
                circuitBreakerFactory.create("search-product-client")
                        .run(action::get, throwable -> {
                            if (throwable instanceof RuntimeException re) throw re;
                            throw new ServiceUnavailableException("Product service unavailable", throwable);
                        })
        ).get();
    }
}
