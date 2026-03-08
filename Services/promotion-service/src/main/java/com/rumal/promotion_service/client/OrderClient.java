package com.rumal.promotion_service.client;

import com.rumal.promotion_service.dto.CustomerPromotionEligibilityResponse;
import com.rumal.promotion_service.exception.ResourceNotFoundException;
import com.rumal.promotion_service.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

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
        this.internalSharedSecret = internalSharedSecret == null ? "" : internalSharedSecret.trim();
    }

    @Retry(name = "orderService")
    @CircuitBreaker(name = "orderService", fallbackMethod = "fallbackGetCustomerPromotionEligibility")
    public CustomerPromotionEligibilityResponse getCustomerPromotionEligibility(UUID customerId) {
        try {
            return restClient
                    .get()
                    .uri("http://order-service/internal/orders/customers/{customerId}/promotion-eligibility", customerId)
                    .header("X-Internal-Auth", internalSharedSecret)
                    .retrieve()
                    .body(CustomerPromotionEligibilityResponse.class);
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResourceNotFoundException("Customer promotion eligibility not found: " + customerId);
            }
            throw ex;
        }
    }

    @SuppressWarnings("unused")
    public CustomerPromotionEligibilityResponse fallbackGetCustomerPromotionEligibility(UUID customerId, Throwable ex) {
        throw new ServiceUnavailableException("Order service unavailable. Try again later.", ex);
    }
}
