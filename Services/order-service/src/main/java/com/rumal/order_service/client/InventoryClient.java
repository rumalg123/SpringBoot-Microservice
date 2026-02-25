package com.rumal.order_service.client;

import com.rumal.order_service.dto.StockCheckRequest;
import com.rumal.order_service.dto.StockCheckResult;
import com.rumal.order_service.dto.StockReserveRequest;
import com.rumal.order_service.dto.StockReservationResponse;
import com.rumal.order_service.exception.ServiceUnavailableException;
import com.rumal.order_service.exception.ValidationException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class InventoryClient {

    private static final ParameterizedTypeReference<List<StockCheckResult>> CHECK_RESULT_LIST =
            new ParameterizedTypeReference<>() {};

    private final RestClient.Builder lbRestClientBuilder;
    private final String internalSharedSecret;

    public InventoryClient(
            @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lbRestClientBuilder,
            @Value("${internal.auth.shared-secret:}") String internalSharedSecret
    ) {
        this.lbRestClientBuilder = lbRestClientBuilder;
        this.internalSharedSecret = internalSharedSecret == null ? "" : internalSharedSecret.trim();
    }

    @Retry(name = "inventoryService")
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "fallbackCheckAvailability")
    public List<StockCheckResult> checkAvailability(List<StockCheckRequest> requests) {
        try {
            List<StockCheckResult> result = lbRestClientBuilder.build()
                    .post()
                    .uri("http://inventory-service/internal/inventory/check")
                    .header("X-Internal-Auth", internalSharedSecret)
                    .body(requests)
                    .retrieve()
                    .body(CHECK_RESULT_LIST);
            return result == null ? List.of() : result;
        } catch (HttpClientErrorException ex) {
            throw mapError(ex, "Stock availability check failed");
        }
    }

    @Retry(name = "inventoryService")
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "fallbackReserveStock")
    public StockReservationResponse reserveStock(UUID orderId, List<StockCheckRequest> items, Instant expiresAt) {
        try {
            return lbRestClientBuilder.build()
                    .post()
                    .uri("http://inventory-service/internal/inventory/reserve")
                    .header("X-Internal-Auth", internalSharedSecret)
                    .body(new StockReserveRequest(orderId, items, expiresAt))
                    .retrieve()
                    .body(StockReservationResponse.class);
        } catch (HttpClientErrorException ex) {
            int code = ex.getStatusCode().value();
            if (code == 409) {
                throw new ValidationException(resolveErrorMessage(ex, "Insufficient stock"));
            }
            throw mapError(ex, "Stock reservation failed");
        }
    }

    @Retry(name = "inventoryService")
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "fallbackConfirmReservation")
    public void confirmReservation(UUID orderId) {
        try {
            lbRestClientBuilder.build()
                    .post()
                    .uri("http://inventory-service/internal/inventory/reservations/{orderId}/confirm", orderId)
                    .header("X-Internal-Auth", internalSharedSecret)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException ex) {
            throw mapError(ex, "Stock reservation confirmation failed");
        }
    }

    @Retry(name = "inventoryService")
    @CircuitBreaker(name = "inventoryService", fallbackMethod = "fallbackReleaseReservation")
    public void releaseReservation(UUID orderId, String reason) {
        try {
            lbRestClientBuilder.build()
                    .post()
                    .uri("http://inventory-service/internal/inventory/reservations/{orderId}/release", orderId)
                    .header("X-Internal-Auth", internalSharedSecret)
                    .body(new ReleaseRequest(reason))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException ex) {
            throw mapError(ex, "Stock reservation release failed");
        }
    }

    // ─── Fallbacks ───

    @SuppressWarnings("unused")
    public List<StockCheckResult> fallbackCheckAvailability(List<StockCheckRequest> requests, Throwable ex) {
        if (ex instanceof ValidationException ve) throw ve;
        throw new ServiceUnavailableException("Inventory service unavailable for stock check. Try again later.", ex);
    }

    @SuppressWarnings("unused")
    public StockReservationResponse fallbackReserveStock(UUID orderId, List<StockCheckRequest> items, Instant expiresAt, Throwable ex) {
        if (ex instanceof ValidationException ve) throw ve;
        throw new ServiceUnavailableException("Inventory service unavailable for stock reservation. Try again later.", ex);
    }

    @SuppressWarnings("unused")
    public void fallbackConfirmReservation(UUID orderId, Throwable ex) {
        if (ex instanceof ValidationException ve) throw ve;
        throw new ServiceUnavailableException("Inventory service unavailable for reservation confirmation. Try again later.", ex);
    }

    @SuppressWarnings("unused")
    public void fallbackReleaseReservation(UUID orderId, String reason, Throwable ex) {
        if (ex instanceof ValidationException ve) throw ve;
        throw new ServiceUnavailableException("Inventory service unavailable for reservation release. Try again later.", ex);
    }

    // ─── Helpers ───

    private RuntimeException mapError(HttpClientErrorException ex, String fallbackMessage) {
        HttpStatusCode status = ex.getStatusCode();
        int code = status.value();
        if (code == 400 || code == 422) {
            throw new ValidationException(resolveErrorMessage(ex, fallbackMessage));
        }
        if (code == 401 || code == 403) {
            throw new ServiceUnavailableException("Inventory service rejected internal authentication.", ex);
        }
        throw new ServiceUnavailableException("Inventory service error: " + fallbackMessage, ex);
    }

    private String resolveErrorMessage(HttpClientErrorException ex, String fallback) {
        String body = ex.getResponseBodyAsString();
        if (body == null || body.isBlank()) return fallback;
        int messageIndex = body.indexOf("\"message\"");
        if (messageIndex >= 0) {
            int colonIndex = body.indexOf(':', messageIndex);
            int quoteStart = body.indexOf('"', colonIndex + 1);
            int quoteEnd = body.indexOf('"', quoteStart + 1);
            if (quoteStart >= 0 && quoteEnd > quoteStart) {
                String message = body.substring(quoteStart + 1, quoteEnd).trim();
                if (!message.isEmpty()) return message;
            }
        }
        return fallback;
    }

    private record ReleaseRequest(String reason) {}
}
