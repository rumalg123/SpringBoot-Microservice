package com.rumal.cart_service.client;

import com.rumal.cart_service.dto.CreateMyOrderItemRequest;
import com.rumal.cart_service.dto.CreateMyOrderRequest;
import com.rumal.cart_service.dto.OrderResponse;
import com.rumal.cart_service.dto.PromotionCheckoutPricingRequest;
import com.rumal.cart_service.exception.ResourceNotFoundException;
import com.rumal.cart_service.exception.ServiceUnavailableException;
import com.rumal.cart_service.exception.ValidationException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

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
            List<CreateMyOrderItemRequest> items,
            PromotionCheckoutPricingRequest promotionPricing,
            String idempotencyKey
    ) {
        RestClient rc = lbRestClientBuilder.build();
        CreateMyOrderRequest request = new CreateMyOrderRequest(items, shippingAddressId, billingAddressId, promotionPricing);

        try {
            RestClient.RequestBodySpec spec = rc.post()
                    .uri("http://order-service/orders/me")
                    .header("X-User-Sub", keycloakId)
                    .header("X-User-Email-Verified", "true")
                    .header("X-Internal-Auth", internalSharedSecret);
            if (StringUtils.hasText(idempotencyKey)) {
                spec = spec.header("Idempotency-Key", idempotencyKey.trim());
            }
            return spec.body(request)
                    .retrieve()
                    .body(OrderResponse.class);
        } catch (HttpClientErrorException ex) {
            HttpStatusCode status = ex.getStatusCode();
            int statusCode = status.value();
            if (statusCode == 400 || statusCode == 409 || statusCode == 422) {
                throw new ValidationException(resolveErrorMessage(ex, "Checkout validation failed"));
            }
            if (statusCode == 404) {
                throw new ResourceNotFoundException(resolveErrorMessage(ex, "Required checkout resource was not found"));
            }
            if (statusCode == 401 || statusCode == 403) {
                throw new ServiceUnavailableException("Order service rejected internal authentication.", ex);
            }
            throw new ServiceUnavailableException("Order service error during checkout.", ex);
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Service unavailable: " + ex.getMessage());
        }
    }

    @SuppressWarnings("unused")
    public OrderResponse orderFallbackCreate(
            String keycloakId,
            UUID shippingAddressId,
            UUID billingAddressId,
            List<CreateMyOrderItemRequest> items,
            PromotionCheckoutPricingRequest promotionPricing,
            String idempotencyKey,
            Throwable ex
    ) {
        if (ex instanceof ValidationException ve) throw ve;
        if (ex instanceof ResourceNotFoundException rnfe) throw rnfe;
        throw new ServiceUnavailableException(
                "Order service unavailable for checkout. Try again later.",
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
