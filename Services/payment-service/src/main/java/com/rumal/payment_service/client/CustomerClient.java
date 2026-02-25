package com.rumal.payment_service.client;

import com.rumal.payment_service.dto.CustomerAddressSummary;
import com.rumal.payment_service.dto.CustomerSummary;
import com.rumal.payment_service.exception.ResourceNotFoundException;
import com.rumal.payment_service.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Component
public class CustomerClient {

    private static final ParameterizedTypeReference<List<CustomerAddressSummary>> ADDRESS_LIST_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient.Builder lbRestClientBuilder;
    private final String internalSharedSecret;

    public CustomerClient(
            @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lbRestClientBuilder,
            @Value("${internal.auth.shared-secret:}") String internalSharedSecret
    ) {
        this.lbRestClientBuilder = lbRestClientBuilder;
        this.internalSharedSecret = internalSharedSecret;
    }

    @Retry(name = "customerService")
    @CircuitBreaker(name = "customerService", fallbackMethod = "getCustomerFallback")
    public CustomerSummary getCustomerByKeycloakId(String keycloakId) {
        try {
            return lbRestClientBuilder.build()
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
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Customer service unavailable: " + ex.getMessage(), ex);
        }
    }

    @Retry(name = "customerService")
    @CircuitBreaker(name = "customerService", fallbackMethod = "getAddressesFallback")
    public List<CustomerAddressSummary> getCustomerAddresses(String keycloakId) {
        try {
            return lbRestClientBuilder.build()
                    .get()
                    .uri("http://customer-service/customers/me/addresses")
                    .header("X-User-Sub", keycloakId)
                    .header("X-User-Email-Verified", "true")
                    .header("X-Internal-Auth", internalSharedSecret)
                    .retrieve()
                    .body(ADDRESS_LIST_TYPE);
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResourceNotFoundException("Customer not found for keycloak id");
            }
            throw ex;
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Customer service unavailable: " + ex.getMessage(), ex);
        }
    }

    @SuppressWarnings("unused")
    public CustomerSummary getCustomerFallback(String keycloakId, Throwable ex) {
        if (ex instanceof ResourceNotFoundException rnfe) throw rnfe;
        throw new ServiceUnavailableException("Customer service unavailable. Try again later.", ex);
    }

    @SuppressWarnings("unused")
    public List<CustomerAddressSummary> getAddressesFallback(String keycloakId, Throwable ex) {
        if (ex instanceof ResourceNotFoundException rnfe) throw rnfe;
        throw new ServiceUnavailableException("Customer service unavailable. Try again later.", ex);
    }
}
