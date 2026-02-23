package com.rumal.wishlist_service.client;

import com.rumal.wishlist_service.dto.ProductDetails;
import com.rumal.wishlist_service.exception.ResourceNotFoundException;
import com.rumal.wishlist_service.exception.ServiceUnavailableException;
import com.rumal.wishlist_service.exception.ValidationException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
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
    public ProductDetails getById(UUID productId) {
        RestClient client = lbRestClientBuilder.build();
        try {
            return client.get()
                    .uri("http://product-service/products/{id}", productId)
                    .retrieve()
                    .body(ProductDetails.class);
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new ResourceNotFoundException("Product not found: " + productId);
            }
            if (ex.getStatusCode().is4xxClientError()) {
                throw new ValidationException(resolveErrorMessage(ex, "Invalid product request"));
            }
            throw new ServiceUnavailableException("Product service error for product " + productId + ".", ex);
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Service unavailable: " + ex.getMessage());
        }
    }

    @SuppressWarnings("unused")
    public ProductDetails productFallbackGetById(UUID productId, Throwable ex) {
        throw new ServiceUnavailableException(
                "Product service unavailable for product " + productId + ". Try again later.",
                ex
        );
    }

    private String resolveErrorMessage(HttpClientErrorException ex, String fallback) {
        String body = ex.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return fallback;
        }
        int messageIndex = body.indexOf("\"message\"");
        if (messageIndex >= 0) {
            int colonIndex = body.indexOf(':', messageIndex);
            int quoteStart = body.indexOf('"', colonIndex + 1);
            int quoteEnd = body.indexOf('"', quoteStart + 1);
            if (quoteStart >= 0 && quoteEnd > quoteStart) {
                String message = body.substring(quoteStart + 1, quoteEnd).trim();
                if (!message.isEmpty()) {
                    return message;
                }
            }
        }
        return fallback;
    }
}
