package com.rumal.order_service.client;

import com.rumal.order_service.dto.VendorStaffAccessLookupResponse;
import com.rumal.order_service.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.util.List;

@Component
public class AccessClient {

    private static final ParameterizedTypeReference<List<VendorStaffAccessLookupResponse>> LIST_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    public AccessClient(@Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lbRestClientBuilder) {
        this.restClient = lbRestClientBuilder.build();
    }

    @Retry(name = "accessService")
    @CircuitBreaker(name = "accessService", fallbackMethod = "fallbackListVendorStaffAccessByKeycloakUser")
    public List<VendorStaffAccessLookupResponse> listVendorStaffAccessByKeycloakUser(String keycloakUserId, String internalAuth) {
        RestClient rc = restClient;
        try {
            List<VendorStaffAccessLookupResponse> response = rc.get()
                    .uri(buildUri("/internal/access/vendors/by-keycloak/" + keycloakUserId))
                    .header("X-Internal-Auth", internalAuth)
                    .retrieve()
                    .body(LIST_TYPE);
            return response == null ? List.of() : response;
        } catch (RestClientResponseException ex) {
            throw new ServiceUnavailableException("Access service vendor lookup failed (" + ex.getStatusCode().value() + ")", ex);
        } catch (RestClientException | IllegalStateException ex) {
            throw new ServiceUnavailableException("Access service unavailable for vendor lookup", ex);
        }
    }

    @SuppressWarnings("unused")
    public List<VendorStaffAccessLookupResponse> fallbackListVendorStaffAccessByKeycloakUser(
            String keycloakUserId,
            String internalAuth,
            Throwable ex
    ) {
        throw new ServiceUnavailableException("Access service unavailable for vendor lookup. Try again later.", ex);
    }

    private URI buildUri(String path) {
        return URI.create("http://access-service" + path);
    }
}
