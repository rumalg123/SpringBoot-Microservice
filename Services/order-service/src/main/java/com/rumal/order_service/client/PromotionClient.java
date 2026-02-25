package com.rumal.order_service.client;

import com.rumal.order_service.dto.CommitCouponReservationRequest;
import com.rumal.order_service.dto.CouponReservationResponse;
import com.rumal.order_service.dto.ReleaseCouponReservationRequest;
import com.rumal.order_service.exception.ResourceNotFoundException;
import com.rumal.order_service.exception.ServiceUnavailableException;
import com.rumal.order_service.exception.ValidationException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class PromotionClient {

    private final RestClient.Builder lbRestClientBuilder;
    private final String internalSharedSecret;

    public PromotionClient(
            @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lbRestClientBuilder,
            @Value("${internal.auth.shared-secret:}") String internalSharedSecret
    ) {
        this.lbRestClientBuilder = lbRestClientBuilder;
        this.internalSharedSecret = internalSharedSecret == null ? "" : internalSharedSecret.trim();
    }

    @Retry(name = "promotionService")
    @CircuitBreaker(name = "promotionService", fallbackMethod = "promotionFallbackCommit")
    public CouponReservationResponse commitCouponReservation(UUID reservationId, UUID orderId) {
        try {
            return lbRestClientBuilder.build()
                    .post()
                    .uri("http://promotion-service/internal/promotions/reservations/{reservationId}/commit", reservationId)
                    .header("X-Internal-Auth", internalSharedSecret)
                    .body(new CommitCouponReservationRequest(orderId))
                    .retrieve()
                    .body(CouponReservationResponse.class);
        } catch (HttpClientErrorException ex) {
            int code = ex.getStatusCode().value();
            if (code == 400 || code == 409 || code == 422) {
                throw new ValidationException(resolveErrorMessage(ex, "Coupon reservation commit failed"));
            }
            if (code == 404) {
                throw new ResourceNotFoundException(resolveErrorMessage(ex, "Coupon reservation not found"));
            }
            if (code == 401 || code == 403) {
                throw new ServiceUnavailableException("Promotion service rejected internal authentication.", ex);
            }
            throw new ServiceUnavailableException("Promotion service error during coupon reservation commit.", ex);
        }
    }

    @Retry(name = "promotionService")
    @CircuitBreaker(name = "promotionService", fallbackMethod = "promotionFallbackRelease")
    public CouponReservationResponse releaseCouponReservation(UUID reservationId, String reason) {
        try {
            return lbRestClientBuilder.build()
                    .post()
                    .uri("http://promotion-service/internal/promotions/reservations/{reservationId}/release", reservationId)
                    .header("X-Internal-Auth", internalSharedSecret)
                    .body(new ReleaseCouponReservationRequest(reason))
                    .retrieve()
                    .body(CouponReservationResponse.class);
        } catch (HttpClientErrorException ex) {
            HttpStatusCode status = ex.getStatusCode();
            int code = status.value();
            if (code == 400 || code == 404 || code == 409 || code == 422) {
                throw new ValidationException(resolveErrorMessage(ex, "Coupon reservation release failed"));
            }
            if (code == 401 || code == 403) {
                throw new ServiceUnavailableException("Promotion service rejected internal authentication.", ex);
            }
            throw new ServiceUnavailableException("Promotion service error during coupon reservation release.", ex);
        }
    }

    @SuppressWarnings("unused")
    public CouponReservationResponse promotionFallbackCommit(UUID reservationId, UUID orderId, Throwable ex) {
        if (ex instanceof ValidationException ve) throw ve;
        if (ex instanceof ResourceNotFoundException rnfe) throw rnfe;
        throw new ServiceUnavailableException("Promotion service unavailable for coupon reservation commit. Try again later.", ex);
    }

    @SuppressWarnings("unused")
    public CouponReservationResponse promotionFallbackRelease(UUID reservationId, String reason, Throwable ex) {
        if (ex instanceof ValidationException ve) throw ve;
        if (ex instanceof ResourceNotFoundException rnfe) throw rnfe;
        throw new ServiceUnavailableException("Promotion service unavailable for coupon reservation release. Try again later.", ex);
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
