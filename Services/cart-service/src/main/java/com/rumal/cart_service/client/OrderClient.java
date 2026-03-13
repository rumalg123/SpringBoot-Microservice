package com.rumal.cart_service.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rumal.cart_service.dto.CreateMyOrderItemRequest;
import com.rumal.cart_service.dto.CreateMyOrderRequest;
import com.rumal.cart_service.dto.OrderResponse;
import com.rumal.cart_service.dto.PromotionCheckoutPricingRequest;
import com.rumal.cart_service.exception.ConflictException;
import com.rumal.cart_service.exception.ResourceNotFoundException;
import com.rumal.cart_service.exception.ServiceUnavailableException;
import com.rumal.cart_service.exception.ValidationException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;

@Component
public class OrderClient {

    private static final Logger log = LoggerFactory.getLogger(OrderClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CREATE_ORDER_URI = "http://order-service/orders/me";

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
        CreateMyOrderRequest request = new CreateMyOrderRequest(items, shippingAddressId, billingAddressId, promotionPricing);
        try {
            return buildCreateOrderRequest(keycloakId, idempotencyKey)
                    .body(request)
                    .retrieve()
                    .body(OrderResponse.class);
        } catch (HttpClientErrorException ex) {
            throw translateOrderServiceError(ex);
        } catch (ResourceAccessException ex) {
            throw translateOrderServiceAccessError(ex);
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Order service unavailable during checkout.", ex);
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
        if (ex instanceof ConflictException ce) throw ce;
        if (ex instanceof ValidationException ve) throw ve;
        if (ex instanceof ResourceNotFoundException rnfe) throw rnfe;
        if (isReadTimeout(ex)) {
            throw new ConflictException(
                    "Checkout request timed out while order placement may still be processing. Check My Orders before retrying.",
                    ex
            );
        }
        throw new ServiceUnavailableException(
                "Order service unavailable for checkout. Try again later.",
                ex
        );
    }

    private String resolveErrorMessage(HttpClientErrorException ex, String fallback) {
        String body = ex.getResponseBodyAsString();
        if (body.isBlank()) {
            return fallback;
        }
        try {
            JsonNode tree = MAPPER.readTree(body);
            String fieldErrorMessage = resolveFieldErrorMessage(tree.get("fieldErrors"));
            if (fieldErrorMessage != null) {
                return fieldErrorMessage;
            }
            String message = textValue(tree, "message");
            if (message != null) {
                return message;
            }
            String error = textValue(tree, "error");
            if (error != null) {
                return error;
            }
        } catch (Exception ignored) {
            // Non-JSON response falls through to the fallback value.
        }
        return fallback;
    }

    private boolean isReadTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("read timed out")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private RestClient.RequestBodySpec buildCreateOrderRequest(String keycloakId, String idempotencyKey) {
        RestClient.RequestBodySpec spec = restClient.post()
                .uri(CREATE_ORDER_URI)
                .header("X-User-Sub", keycloakId)
                .header("X-User-Email-Verified", "true")
                .header("X-Internal-Auth", internalSharedSecret);
        if (StringUtils.hasText(idempotencyKey)) {
            return spec.header("Idempotency-Key", idempotencyKey.trim());
        }
        return spec;
    }

    private RuntimeException translateOrderServiceError(HttpClientErrorException ex) {
        HttpStatusCode status = ex.getStatusCode();
        int statusCode = status.value();
        log.warn("Order service returned HTTP {} during checkout: {}", statusCode, ex.getResponseBodyAsString(), ex);
        if (statusCode == 409) {
            return new ConflictException(
                    resolveErrorMessage(ex, "Checkout request with this idempotency key is still processing"),
                    ex
            );
        }
        if (statusCode == 400 || statusCode == 422) {
            return new ValidationException(resolveErrorMessage(ex, "Checkout validation failed"), ex);
        }
        if (statusCode == 404) {
            return new ResourceNotFoundException(resolveErrorMessage(ex, "Required checkout resource was not found"), ex);
        }
        if (statusCode == 401 || statusCode == 403) {
            return new ServiceUnavailableException("Order service rejected internal authentication.", ex);
        }
        return new ServiceUnavailableException("Order service error during checkout.", ex);
    }

    private RuntimeException translateOrderServiceAccessError(ResourceAccessException ex) {
        if (isReadTimeout(ex)) {
            return new ConflictException(
                    "Checkout request timed out while order placement may still be processing. Check My Orders before retrying.",
                    ex
            );
        }
        return new ServiceUnavailableException("Order service unavailable during checkout.", ex);
    }

    private String resolveFieldErrorMessage(JsonNode fieldErrors) {
        if (fieldErrors == null || !fieldErrors.isObject()) {
            return null;
        }
        StringJoiner joiner = new StringJoiner("; ");
        var fieldNames = fieldErrors.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldError = textValue(fieldErrors, fieldNames.next());
            if (fieldError != null) {
                joiner.add(fieldError);
            }
        }
        return joiner.length() == 0 ? null : joiner.toString();
    }

    private String textValue(JsonNode tree, String fieldName) {
        JsonNode value = tree.get(fieldName);
        if (value == null) {
            return null;
        }
        String text = value.asText();
        return text.isBlank() ? null : text;
    }
}
