package com.rumal.review_service.client;

import com.rumal.review_service.dto.PlatformAccessLookupResponse;
import com.rumal.review_service.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.util.Set;

@Component
public class AccessClient {

    private final RestClient restClient;

    public AccessClient(@Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lbRestClientBuilder) {
        this.restClient = lbRestClientBuilder.build();
    }

    @Retry(name = "accessService")
    @CircuitBreaker(name = "accessService", fallbackMethod = "fallbackGetPlatformAccessByKeycloakUser")
    public PlatformAccessLookupResponse getPlatformAccessByKeycloakUser(String keycloakUserId, String internalAuth) {
        RestClient rc = restClient;
        try {
            PlatformAccessLookupResponse response = rc.get()
                    .uri(buildUri("/internal/access/platform/by-keycloak/" + keycloakUserId))
                    .header("X-Internal-Auth", internalAuth)
                    .retrieve()
                    .body(PlatformAccessLookupResponse.class);
            return response == null ? new PlatformAccessLookupResponse(keycloakUserId, false, Set.of()) : response;
        } catch (RestClientResponseException ex) {
            throw new ServiceUnavailableException("Access service platform lookup failed (" + ex.getStatusCode().value() + ")", ex);
        } catch (RestClientException | IllegalStateException ex) {
            throw new ServiceUnavailableException("Access service unavailable for platform lookup", ex);
        }
    }

    @SuppressWarnings("unused")
    public PlatformAccessLookupResponse fallbackGetPlatformAccessByKeycloakUser(String keycloakUserId, String internalAuth, Throwable ex) {
        throw new ServiceUnavailableException("Access service unavailable for platform lookup. Try again later.", ex);
    }

    private URI buildUri(String path) {
        return URI.create("http://access-service" + path);
    }
}
