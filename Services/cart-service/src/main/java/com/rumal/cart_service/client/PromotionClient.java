package com.rumal.cart_service.client;

import com.rumal.cart_service.dto.CouponReservationResponse;
import com.rumal.cart_service.dto.CreateCouponReservationRequest;
import com.rumal.cart_service.dto.PromotionQuoteRequest;
import com.rumal.cart_service.dto.PromotionQuoteResponse;
import com.rumal.cart_service.dto.ReleaseCouponReservationRequest;
import com.rumal.cart_service.exception.ServiceUnavailableException;
import com.rumal.cart_service.exception.ValidationException;
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
    @CircuitBreaker(name = "promotionService", fallbackMethod = "promotionFallbackQuote")
    public PromotionQuoteResponse quote(PromotionQuoteRequest request) {
        try {
            return lbRestClientBuilder.build()
                    .post()
                    .uri("http://promotion-service/internal/promotions/quote")
                    .header("X-Internal-Auth", internalSharedSecret)
                    .body(request)
                    .retrieve()
                    .body(PromotionQuoteResponse.class);
        } catch (HttpClientErrorException ex) {
            HttpStatusCode status = ex.getStatusCode();
            int code = status.value();
            if (code == 400 || code == 409 || code == 422) {
                throw new ValidationException(resolveErrorMessage(ex, "Promotion quote validation failed"));
            }
            if (code == 401 || code == 403) {
                throw new ServiceUnavailableException("Promotion service rejected internal authentication.", ex);
            }
            throw new ServiceUnavailableException("Promotion service error during quote.", ex);
        }
    }

    @Retry(name = "promotionService")
    @CircuitBreaker(name = "promotionService", fallbackMethod = "promotionFallbackReserve")
    public CouponReservationResponse reserveCoupon(CreateCouponReservationRequest request) {
        try {
            return lbRestClientBuilder.build()
                    .post()
                    .uri("http://promotion-service/internal/promotions/reservations")
                    .header("X-Internal-Auth", internalSharedSecret)
                    .body(request)
                    .retrieve()
                    .body(CouponReservationResponse.class);
        } catch (HttpClientErrorException ex) {
            HttpStatusCode status = ex.getStatusCode();
            int code = status.value();
            if (code == 400 || code == 409 || code == 422) {
                throw new ValidationException(resolveErrorMessage(ex, "Coupon reservation failed"));
            }
            if (code == 401 || code == 403) {
                throw new ServiceUnavailableException("Promotion service rejected internal authentication.", ex);
            }
            throw new ServiceUnavailableException("Promotion service error during coupon reservation.", ex);
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
    public PromotionQuoteResponse promotionFallbackQuote(PromotionQuoteRequest request, Throwable ex) {
        if (ex instanceof ValidationException ve) throw ve;
        throw new ServiceUnavailableException("Promotion service unavailable for pricing preview. Try again later.", ex);
    }

    @SuppressWarnings("unused")
    public CouponReservationResponse promotionFallbackReserve(CreateCouponReservationRequest request, Throwable ex) {
        if (ex instanceof ValidationException ve) throw ve;
        throw new ServiceUnavailableException("Promotion service unavailable for coupon reservation. Try again later.", ex);
    }

    @SuppressWarnings("unused")
    public CouponReservationResponse promotionFallbackRelease(UUID reservationId, String reason, Throwable ex) {
        if (ex instanceof ValidationException ve) throw ve;
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
