package com.rumal.cart_service.client;

import com.rumal.cart_service.dto.CreateMyOrderItemRequest;
import com.rumal.cart_service.dto.CreateMyOrderRequest;
import com.rumal.cart_service.dto.OrderResponse;
import com.rumal.cart_service.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

@Component
public class OrderClient {

    private final RestClient.Builder lbRestClientBuilder;
    private final String internalSharedSecret;

    public OrderClient(
            @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lbRestClientBuilder,
            @Value("${internal.auth.shared-secret:}") String internalSharedSecret
    ) {
        this.lbRestClientBuilder = lbRestClientBuilder;
        this.internalSharedSecret = internalSharedSecret;
    }

    @Retry(name = "orderService")
    @CircuitBreaker(name = "orderService", fallbackMethod = "orderFallbackCreate")
    public OrderResponse createMyOrder(
            String keycloakId,
            UUID shippingAddressId,
            UUID billingAddressId,
            List<CreateMyOrderItemRequest> items
    ) {
        RestClient rc = lbRestClientBuilder.build();
        CreateMyOrderRequest request = new CreateMyOrderRequest(items, shippingAddressId, billingAddressId);

        return rc.post()
                .uri("http://order-service/orders/me")
                .header("X-User-Sub", keycloakId)
                .header("X-User-Email-Verified", "true")
                .header("X-Internal-Auth", internalSharedSecret)
                .body(request)
                .retrieve()
                .body(OrderResponse.class);
    }

    @SuppressWarnings("unused")
    public OrderResponse orderFallbackCreate(
            String keycloakId,
            UUID shippingAddressId,
            UUID billingAddressId,
            List<CreateMyOrderItemRequest> items,
            Throwable ex
    ) {
        throw new ServiceUnavailableException(
                "Order service unavailable for checkout. Try again later.",
                ex
        );
    }
}
