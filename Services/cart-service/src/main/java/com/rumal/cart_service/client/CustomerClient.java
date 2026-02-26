package com.rumal.cart_service.client;

import com.rumal.cart_service.dto.CustomerAddressSummary;
import com.rumal.cart_service.dto.CustomerSummary;
import com.rumal.cart_service.exception.ResourceNotFoundException;
import com.rumal.cart_service.exception.ServiceUnavailableException;
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
    @CircuitBreaker(name = "customerService", fallbackMethod = "customerFallbackGetCustomerByKeycloak")
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

    @Retry(name = "customerService")
    @CircuitBreaker(name = "customerService", fallbackMethod = "customerFallbackGetCustomerAddress")
    public CustomerAddressSummary getCustomerAddress(UUID customerId, UUID addressId) {
        try {
            return restClient
                    .get()
                    .uri("http://customer-service/customers/{customerId}/addresses/{addressId}", customerId, addressId)
                    .header("X-Internal-Auth", internalSharedSecret)
                    .retrieve()
                    .body(CustomerAddressSummary.class);
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResourceNotFoundException("Address not found: " + addressId);
            }
            throw ex;
        }
    }

    @SuppressWarnings("unused")
    public CustomerSummary customerFallbackGetCustomerByKeycloak(String keycloakId, Throwable ex) {
        throw new ServiceUnavailableException("Customer service unavailable for principal " + keycloakId + ". Try again later.", ex);
    }

    @SuppressWarnings("unused")
    public CustomerAddressSummary customerFallbackGetCustomerAddress(UUID customerId, UUID addressId, Throwable ex) {
        throw new ServiceUnavailableException(
                "Customer service unavailable for customer " + customerId + " address " + addressId + ". Try again later.",
                ex
        );
    }
}
