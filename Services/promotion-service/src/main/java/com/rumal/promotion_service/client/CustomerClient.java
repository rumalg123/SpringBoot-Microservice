package com.rumal.promotion_service.client;

import com.rumal.promotion_service.dto.CustomerSummary;
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

@Component
public class CustomerClient {

    private final RestClient restClient;
    private final String internalSharedSecret;

    public CustomerClient(
            @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lbRestClientBuilder,
            @Value("${internal.auth.shared-secret:}") String internalSharedSecret
    ) {
        this.restClient = lbRestClientBuilder.build();
        this.internalSharedSecret = internalSharedSecret == null ? "" : internalSharedSecret.trim();
    }

    @Retry(name = "customerService")
    @CircuitBreaker(name = "customerService", fallbackMethod = "fallbackGetCustomerByKeycloakId")
    public CustomerSummary getCustomerByKeycloakId(String keycloakId) {
        try {
            return restClient
                    .get()
                    .uri("http://customer-service/customers/me")
                    .header("X-User-Sub", keycloakId)
                    .header("X-User-Email-Verified", "true")
                    .header("X-Internal-Auth", internalSharedSecret)
                    .retrieve()
                    .body(CustomerSummary.class);
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResourceNotFoundException("Customer not found for keycloak id");
            }
            throw ex;
        }
    }

    @SuppressWarnings("unused")
    public CustomerSummary fallbackGetCustomerByKeycloakId(String keycloakId, Throwable ex) {
        throw new ServiceUnavailableException("Customer service unavailable. Try again later.", ex);
    }
}
