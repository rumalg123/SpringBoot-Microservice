package com.rumal.review_service.client;

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

import java.util.Map;
import java.util.UUID;

@Component
public class VendorClient {

    private static final Logger log = LoggerFactory.getLogger(VendorClient.class);

    private final RestClient restClient;
    private final String internalSharedSecret;

    public VendorClient(
            @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lbRestClientBuilder,
            @Value("${internal.auth.shared-secret:}") String internalSharedSecret) {
        this.restClient = lbRestClientBuilder.build();
        this.internalSharedSecret = internalSharedSecret;
    }

    @Retry(name = "vendorService")
    @CircuitBreaker(name = "vendorService", fallbackMethod = "fallbackGetVendorIdByKeycloakSub")
    @SuppressWarnings("unchecked")
    public UUID getVendorIdByKeycloakSub(String keycloakSub) {
        RestClient rc = restClient;
        try {
            Map<String, Object> response = rc.get()
                    .uri("http://vendor-service/internal/vendors/keycloak/{keycloakSub}", keycloakSub)
                    .header("X-Internal-Auth", internalSharedSecret)
                    .retrieve()
                    .body(Map.class);
            if (response != null && response.get("id") != null) {
                return UUID.fromString(response.get("id").toString());
            }
            throw new ResourceNotFoundException("Vendor not found for keycloak sub: " + keycloakSub);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ResourceNotFoundException("Vendor not found for keycloak sub: " + keycloakSub);
        } catch (HttpClientErrorException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Vendor service unavailable", ex);
        }
    }

    @SuppressWarnings("unused")
    public UUID fallbackGetVendorIdByKeycloakSub(String keycloakSub, Throwable ex) {
        log.warn("Vendor service unavailable for keycloak sub {}", keycloakSub, ex);
        throw new ServiceUnavailableException("Vendor service unavailable", ex);
    }
}
