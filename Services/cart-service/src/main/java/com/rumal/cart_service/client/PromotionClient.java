package com.rumal.cart_service.client;

import com.rumal.cart_service.dto.PromotionQuoteRequest;
import com.rumal.cart_service.dto.PromotionQuoteResponse;
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

    @SuppressWarnings("unused")
    public PromotionQuoteResponse promotionFallbackQuote(PromotionQuoteRequest request, Throwable ex) {
        throw new ServiceUnavailableException("Promotion service unavailable for pricing preview. Try again later.", ex);
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
