package com.rumal.order_service.client;

import com.rumal.order_service.dto.ProductSummary;
import com.rumal.order_service.exception.ResourceNotFoundException;
import com.rumal.order_service.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Component
public class ProductClient {

    private final RestClient.Builder lbRestClientBuilder;

    public ProductClient(@Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lbRestClientBuilder) {
        this.lbRestClientBuilder = lbRestClientBuilder;
    }

    @Retry(name = "productService")
    @CircuitBreaker(name = "productService", fallbackMethod = "productFallbackGetById")
    public ProductSummary getById(UUID productId) {
        RestClient rc = lbRestClientBuilder.build();
        try {
            return rc.get()
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
        throw new ServiceUnavailableException("Product service unavailable for product " + productId + ". Try again later.", ex);
    }
}
