package com.rumal.wishlist_service.client;

import com.rumal.wishlist_service.exception.ServiceUnavailableException;
import com.rumal.wishlist_service.exception.ValidationException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.UUID;

@Component
public class CartClient {

    private final RestClient restClient;
    private final String internalAuthSecret;

    public CartClient(
            @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lbRestClientBuilder,
            @Value("${internal.auth.shared-secret:}") String internalAuthSecret
    ) {
        this.restClient = lbRestClientBuilder.build();
        this.internalAuthSecret = internalAuthSecret;
    }

    @Retry(name = "cartService")
    @CircuitBreaker(name = "cartService", fallbackMethod = "addItemFallback")
    public void addItemToCart(String keycloakId, UUID productId, int quantity) {
        RestClient client = restClient;
        try {
            client.post()
                    .uri("http://cart-service/cart/me/items")
                    .header("X-User-Sub", keycloakId)
                    .header("X-User-Email-Verified", "true")
                    .header("X-Internal-Auth", internalAuthSecret)
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("productId", productId, "quantity", quantity))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                throw new ValidationException(resolveErrorMessage(ex, "Failed to add item to cart"));
            }
            throw new ServiceUnavailableException("Cart service error.", ex);
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Cart service unavailable: " + ex.getMessage());
        }
    }

    @SuppressWarnings("unused")
    public void addItemFallback(String keycloakId, UUID productId, int quantity, Throwable ex) {
        if (ex instanceof ValidationException ve) throw ve;
        throw new ServiceUnavailableException(
                "Cart service unavailable. Try again later.",
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
