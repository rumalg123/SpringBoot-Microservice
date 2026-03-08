package com.rumal.inventory_service.client;

import com.rumal.inventory_service.dto.OrderStatusSnapshot;
import com.rumal.inventory_service.exception.ResourceNotFoundException;
import com.rumal.inventory_service.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Component
public class OrderClient {

    private final RestClient restClient;
    private final String internalSharedSecret;

    public OrderClient(
            @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lbRestClientBuilder,
            @Value("${internal.auth.shared-secret:}") String internalSharedSecret
    ) {
        this.restClient = lbRestClientBuilder.build();
        this.internalSharedSecret = internalSharedSecret;
    }

    @Retry(name = "orderService")
    @CircuitBreaker(name = "orderService", fallbackMethod = "getOrderStatusFallback")
    public OrderStatusSnapshot getOrderStatus(UUID orderId) {
        try {
            return restClient
                    .get()
                    .uri("http://order-service/orders/{id}", orderId)
                    .header("X-Internal-Auth", internalSharedSecret)
                    .retrieve()
                    .body(OrderStatusSnapshot.class);
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResourceNotFoundException("Order not found: " + orderId);
            }
            throw new ServiceUnavailableException("Order service error while reading order status.", ex);
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Order service unavailable: " + ex.getMessage(), ex);
        }
    }

    @SuppressWarnings("unused")
    public OrderStatusSnapshot getOrderStatusFallback(UUID orderId, Throwable ex) {
        if (ex instanceof ResourceNotFoundException rnfe) {
            throw rnfe;
        }
        throw new ServiceUnavailableException("Order service unavailable while reading order status. Try again later.", ex);
    }
}
