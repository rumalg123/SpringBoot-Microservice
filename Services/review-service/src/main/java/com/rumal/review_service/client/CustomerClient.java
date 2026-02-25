package com.rumal.review_service.client;

import com.rumal.review_service.dto.CustomerSummary;
import com.rumal.review_service.exception.ResourceNotFoundException;
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
public class CustomerClient {

    private static final Logger log = LoggerFactory.getLogger(CustomerClient.class);

    private final RestClient.Builder lbRestClientBuilder;
    private final String internalSharedSecret;

    public CustomerClient(
            @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lbRestClientBuilder,
            @Value("${internal.auth.shared-secret:}") String internalSharedSecret) {
        this.lbRestClientBuilder = lbRestClientBuilder;
        this.internalSharedSecret = internalSharedSecret;
    }

    @Retry(name = "customerService")
    @CircuitBreaker(name = "customerService", fallbackMethod = "fallbackGetByKeycloakId")
    public CustomerSummary getByKeycloakId(String keycloakId) {
        RestClient rc = lbRestClientBuilder.build();
        try {
            return rc.get()
                    .uri("http://customer-service/internal/customers/keycloak/{keycloakId}", keycloakId)
                    .header("X-Internal-Auth", internalSharedSecret)
                    .retrieve()
                    .body(CustomerSummary.class);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ResourceNotFoundException("Customer not found for keycloak ID: " + keycloakId);
        } catch (HttpClientErrorException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Customer service unavailable", ex);
        }
    }

    @SuppressWarnings("unused")
    public CustomerSummary fallbackGetByKeycloakId(String keycloakId, Throwable ex) {
        log.warn("Customer service unavailable for keycloak ID {}. No fallback available.", keycloakId, ex);
        throw new ServiceUnavailableException("Customer service unavailable", ex);
    }
}
