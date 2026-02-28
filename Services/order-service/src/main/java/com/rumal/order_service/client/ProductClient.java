package com.rumal.order_service.client;

import com.rumal.order_service.dto.ProductSummary;
import com.rumal.order_service.exception.ResourceNotFoundException;
import com.rumal.order_service.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class ProductClient {

    private final RestClient restClient;
    private final String internalSecret;

    public ProductClient(
            @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lbRestClientBuilder,
            @Value("${internal.auth.shared-secret:}") String internalSecret
    ) {
        this.restClient = lbRestClientBuilder.build();
        this.internalSecret = internalSecret;
    }

    @Retry(name = "productService")
    @CircuitBreaker(name = "productService", fallbackMethod = "productFallbackGetById")
    public ProductSummary getById(UUID productId) {
        try {
            return restClient.get()
                    .uri("http://product-service/products/{id}", productId)
                    .retrieve()
                    .body(ProductSummary.class);
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResourceNotFoundException("Product not found: " + productId);
            }
            throw ex;
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Service unavailable: " + ex.getMessage(), ex);
        }
    }

    @SuppressWarnings("unused")
    public ProductSummary productFallbackGetById(UUID productId, Throwable ex) {
        if (ex instanceof ResourceNotFoundException rnfe) throw rnfe;
        throw new ServiceUnavailableException("Product service unavailable for product " + productId + ". Try again later.", ex);
    }

    @Retry(name = "productService")
    @CircuitBreaker(name = "productService", fallbackMethod = "productFallbackGetBatch")
    public List<ProductSummary> getBatch(List<UUID> productIds) {
        try {
            return restClient.post()
                    .uri("http://product-service/internal/products/batch")
                    .header("X-Internal-Auth", internalSecret)
                    .body(Map.of("productIds", productIds))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Product service unavailable for batch lookup: " + ex.getMessage(), ex);
        }
    }

    @SuppressWarnings("unused")
    public List<ProductSummary> productFallbackGetBatch(List<UUID> productIds, Throwable ex) {
        throw new ServiceUnavailableException("Product service unavailable for batch product lookup. Try again later.", ex);
    }
}
