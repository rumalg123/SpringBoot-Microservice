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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;

@Component
public class OrderClient {

    private static final Logger log = LoggerFactory.getLogger(OrderClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

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
    @CircuitBreaker(name = "orderService", fallbackMethod = "orderFallbackCreate")
    public OrderResponse createMyOrder(
            String keycloakId,
            UUID shippingAddressId,
            UUID billingAddressId,
            List<CreateMyOrderItemRequest> items,
            PromotionCheckoutPricingRequest promotionPricing,
            String idempotencyKey
    ) {
        RestClient rc = restClient;
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
            log.warn("Order service returned HTTP {} during checkout: {}", ex.getStatusCode().value(), ex.getResponseBodyAsString());
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
        try {
            JsonNode tree = MAPPER.readTree(body);

            // Check fieldErrors first (from bean validation: MethodArgumentNotValidException)
            JsonNode fieldErrors = tree.get("fieldErrors");
            if (fieldErrors != null && fieldErrors.isObject()) {
                StringJoiner sj = new StringJoiner("; ");
                fieldErrors.fields().forEachRemaining(e -> sj.add(e.getValue().asText()));
                if (sj.length() > 0) {
                    return sj.toString();
                }
            }

            // Fall back to "message" field (from business ValidationException)
            JsonNode message = tree.get("message");
            if (message != null && !message.asText().isBlank()) {
                return message.asText();
            }
        } catch (Exception ignored) {
            // Non-JSON response — fall through to fallback
        }
        return fallback;
    }
}
