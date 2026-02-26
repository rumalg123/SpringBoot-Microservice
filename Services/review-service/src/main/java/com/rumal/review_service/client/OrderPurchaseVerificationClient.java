package com.rumal.review_service.client;

import com.rumal.review_service.dto.CustomerProductPurchaseCheckResponse;
import com.rumal.review_service.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Component
public class OrderPurchaseVerificationClient {

    private static final Logger log = LoggerFactory.getLogger(OrderPurchaseVerificationClient.class);

    private final RestClient restClient;
    private final String internalSharedSecret;

    public OrderPurchaseVerificationClient(
            @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lbRestClientBuilder,
            @Value("${internal.auth.shared-secret:}") String internalSharedSecret) {
        this.restClient = lbRestClientBuilder.build();
        this.internalSharedSecret = internalSharedSecret;
    }

    @Retry(name = "orderService")
    @CircuitBreaker(name = "orderService", fallbackMethod = "fallbackCheckPurchase")
    public CustomerProductPurchaseCheckResponse checkPurchase(UUID customerId, UUID productId) {
        RestClient rc = restClient;
        try {
            return rc.get()
                    .uri("http://order-service/internal/orders/customers/{customerId}/products/{productId}/purchased",
                            customerId, productId)
                    .header("X-Internal-Auth", internalSharedSecret)
                    .retrieve()
                    .body(CustomerProductPurchaseCheckResponse.class);
        } catch (HttpClientErrorException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Order service unavailable while verifying purchase", ex);
        }
    }

    @SuppressWarnings("unused")
    public CustomerProductPurchaseCheckResponse fallbackCheckPurchase(UUID customerId, UUID productId, Throwable ex) {
        log.warn("Order service unavailable for purchase check customerId={} productId={}. No fallback.", customerId, productId, ex);
        throw new ServiceUnavailableException("Order service unavailable while verifying purchase", ex);
    }
}
