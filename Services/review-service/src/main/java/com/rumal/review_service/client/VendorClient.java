package com.rumal.review_service.client;

import com.rumal.review_service.exception.ResourceNotFoundException;
import com.rumal.review_service.exception.ServiceUnavailableException;
import com.rumal.review_service.exception.UnauthorizedException;
import com.rumal.review_service.exception.ValidationException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class VendorClient {

    private static final Logger log = LoggerFactory.getLogger(VendorClient.class);
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_MAP_TYPE =
            new ParameterizedTypeReference<>() {};

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
    public UUID getVendorIdByKeycloakSub(String keycloakSub, UUID vendorIdHint) {
        RestClient rc = restClient;
        try {
            List<Map<String, Object>> response = rc.get()
                    .uri("http://vendor-service/internal/vendors/access/by-keycloak/{keycloakSub}", keycloakSub)
                    .header("X-Internal-Auth", internalSharedSecret)
                    .retrieve()
                    .body(LIST_MAP_TYPE);
            if (response == null || response.isEmpty()) {
                throw new ResourceNotFoundException("Vendor not found for keycloak sub: " + keycloakSub);
            }

            Set<UUID> vendorIds = new LinkedHashSet<>();
            for (Map<String, Object> membership : response) {
                if (membership == null) {
                    continue;
                }
                Object rawVendorId = membership.get("vendorId");
                if (rawVendorId == null) {
                    continue;
                }
                try {
                    vendorIds.add(UUID.fromString(String.valueOf(rawVendorId)));
                } catch (IllegalArgumentException ignored) {
                    // Ignore malformed downstream row.
                }
            }

            if (vendorIds.isEmpty()) {
                throw new ResourceNotFoundException("Vendor not found for keycloak sub: " + keycloakSub);
            }
            if (vendorIdHint != null) {
                if (!vendorIds.contains(vendorIdHint)) {
                    throw new UnauthorizedException("You cannot access another vendor");
                }
                return vendorIdHint;
            }
            if (vendorIds.size() > 1) {
                throw new ValidationException("Multiple vendors found for this user. Specify vendorId context.");
            }
            return vendorIds.iterator().next();
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ResourceNotFoundException("Vendor not found for keycloak sub: " + keycloakSub);
        } catch (HttpClientErrorException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Vendor service unavailable", ex);
        }
    }

    @SuppressWarnings("unused")
    public UUID fallbackGetVendorIdByKeycloakSub(String keycloakSub, UUID vendorIdHint, Throwable ex) {
        log.warn("Vendor service unavailable for keycloak sub {}", keycloakSub, ex);
        throw new ServiceUnavailableException("Vendor service unavailable", ex);
    }
}
