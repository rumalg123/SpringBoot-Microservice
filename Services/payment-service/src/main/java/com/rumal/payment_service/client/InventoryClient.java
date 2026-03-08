package com.rumal.payment_service.client;

import com.rumal.payment_service.dto.InventoryReservationReadinessResponse;
import com.rumal.payment_service.exception.ServiceUnavailableException;
import com.rumal.payment_service.exception.ValidationException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Component
public class InventoryClient {

    private final RestClient restClient;
    private final String internalSharedSecret;

    public InventoryClient(
            @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lbRestClientBuilder,
            @Value("${internal.auth.shared-secret:}") String internalSharedSecret
    ) {
        this.restClient = lbRestClientBuilder.build();
        this.internalSharedSecret = internalSharedSecret;
    }

    @Retry(name = "inventoryService")
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "reservationReadinessFallback")
    public boolean isReservationReadyForPayment(UUID orderId) {
        try {
            InventoryReservationReadinessResponse response = restClient
                    .get()
                    .uri("http://inventory-service/internal/inventory/reservations/{orderId}/readiness", orderId)
                    .header("X-Internal-Auth", internalSharedSecret)
                    .retrieve()
                    .body(InventoryReservationReadinessResponse.class);
            return response != null && response.readyForPayment();
        } catch (HttpClientErrorException ex) {
            int code = ex.getStatusCode().value();
            if (code == 400 || code == 422) {
                throw new ValidationException("Invalid inventory reservation readiness check request");
            }
            if (code == 401 || code == 403) {
                throw new ServiceUnavailableException("Inventory service rejected internal authentication.", ex);
            }
            throw new ServiceUnavailableException("Inventory service error while validating reservation readiness.", ex);
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Inventory service unavailable: " + ex.getMessage(), ex);
        }
    }

    @SuppressWarnings("unused")
    public boolean reservationReadinessFallback(UUID orderId, Throwable ex) {
        if (ex instanceof ValidationException ve) throw ve;
        throw new ServiceUnavailableException(
                "Inventory service unavailable while validating reservation readiness. Try again later.",
                ex
        );
    }

    @Retry(name = "inventoryService")
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "confirmReservationFallback")
    public void confirmReservation(UUID orderId) {
        try {
            restClient
                    .post()
                    .uri("http://inventory-service/internal/inventory/reservations/{orderId}/confirm", orderId)
                    .header("X-Internal-Auth", internalSharedSecret)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException ex) {
            int code = ex.getStatusCode().value();
            if (code == 400 || code == 404 || code == 409 || code == 422) {
                throw new ValidationException(resolveErrorMessage(ex, "Inventory reservation confirmation failed"));
            }
            if (code == 401 || code == 403) {
                throw new ServiceUnavailableException("Inventory service rejected internal authentication.", ex);
            }
            throw new ServiceUnavailableException("Inventory service error while confirming reservation.", ex);
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Inventory service unavailable: " + ex.getMessage(), ex);
        }
    }

    @Retry(name = "inventoryService")
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "releaseReservationFallback")
    public void releaseReservation(UUID orderId, String reason) {
        try {
            restClient
                    .post()
                    .uri("http://inventory-service/internal/inventory/reservations/{orderId}/release", orderId)
                    .header("X-Internal-Auth", internalSharedSecret)
                    .body(new ReleaseRequest(reason))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException ex) {
            int code = ex.getStatusCode().value();
            if (code == 400 || code == 404 || code == 409 || code == 422) {
                throw new ValidationException(resolveErrorMessage(ex, "Inventory reservation release failed"));
            }
            if (code == 401 || code == 403) {
                throw new ServiceUnavailableException("Inventory service rejected internal authentication.", ex);
            }
            throw new ServiceUnavailableException("Inventory service error while releasing reservation.", ex);
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Inventory service unavailable: " + ex.getMessage(), ex);
        }
    }

    @SuppressWarnings("unused")
    public void confirmReservationFallback(UUID orderId, Throwable ex) {
        if (ex instanceof ValidationException ve) {
            throw ve;
        }
        throw new ServiceUnavailableException(
                "Inventory service unavailable while confirming reservation. Try again later.",
                ex
        );
    }

    @SuppressWarnings("unused")
    public void releaseReservationFallback(UUID orderId, String reason, Throwable ex) {
        if (ex instanceof ValidationException ve) {
            throw ve;
        }
        throw new ServiceUnavailableException(
                "Inventory service unavailable while releasing reservation. Try again later.",
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
        int errorIndex = body.indexOf("\"error\"");
        if (errorIndex >= 0) {
            int colonIndex = body.indexOf(':', errorIndex);
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

    private record ReleaseRequest(String reason) {
    }
}
