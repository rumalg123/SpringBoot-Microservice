package com.rumal.product_service.client;

import com.rumal.product_service.dto.VendorOperationalStateResponse;
import com.rumal.product_service.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class VendorOperationalStateClient {

    private static final Logger log = LoggerFactory.getLogger(VendorOperationalStateClient.class);

    private static final ParameterizedTypeReference<List<VendorOperationalStateResponse>> LIST_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient.Builder lbRestClientBuilder;

    public VendorOperationalStateClient(@Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lbRestClientBuilder) {
        this.lbRestClientBuilder = lbRestClientBuilder;
    }

    @Retry(name = "vendorService")
    @CircuitBreaker(name = "vendorService", fallbackMethod = "fallbackGetState")
    public VendorOperationalStateResponse getState(UUID vendorId, String internalAuth) {
        RestClient rc = lbRestClientBuilder.build();
        try {
            return rc.get()
                    .uri(buildUri("/internal/vendors/access/operational-state/" + vendorId))
                    .header("X-Internal-Auth", internalAuth)
                    .retrieve()
                    .body(VendorOperationalStateResponse.class);
        } catch (RestClientResponseException ex) {
            throw new ServiceUnavailableException("Vendor service operational-state lookup failed (" + ex.getStatusCode().value() + ")", ex);
        } catch (RestClientException | IllegalStateException ex) {
            throw new ServiceUnavailableException("Vendor service unavailable for operational-state lookup", ex);
        }
    }

    @Retry(name = "vendorService")
    @CircuitBreaker(name = "vendorService", fallbackMethod = "fallbackGetStates")
    public Map<UUID, VendorOperationalStateResponse> getStates(Collection<UUID> vendorIds, String internalAuth) {
        if (vendorIds == null || vendorIds.isEmpty()) {
            return Map.of();
        }
        RestClient rc = lbRestClientBuilder.build();
        try {
            List<VendorOperationalStateResponse> rows = rc.post()
                    .uri(buildUri("/internal/vendors/access/operational-state/batch"))
                    .header("X-Internal-Auth", internalAuth)
                    .body(vendorIds.stream().distinct().toList())
                    .retrieve()
                    .body(LIST_TYPE);
            if (rows == null || rows.isEmpty()) {
                return Map.of();
            }
            return rows.stream()
                    .filter(row -> row != null && row.vendorId() != null)
                    .collect(Collectors.toMap(VendorOperationalStateResponse::vendorId, Function.identity(), (a, b) -> a));
        } catch (RestClientResponseException ex) {
            throw new ServiceUnavailableException("Vendor service operational-state batch lookup failed (" + ex.getStatusCode().value() + ")", ex);
        } catch (RestClientException | IllegalStateException ex) {
            throw new ServiceUnavailableException("Vendor service unavailable for operational-state batch lookup", ex);
        }
    }

    @SuppressWarnings("unused")
    public VendorOperationalStateResponse fallbackGetState(UUID vendorId, String internalAuth, Throwable ex) {
        log.warn("Vendor service unavailable for operational-state lookup (vendorId={}). Falling back to visible default.", vendorId, ex);
        return new VendorOperationalStateResponse(vendorId, null, true, false, "APPROVED", true, true, true);
    }

    @SuppressWarnings("unused")
    public Map<UUID, VendorOperationalStateResponse> fallbackGetStates(Collection<UUID> vendorIds, String internalAuth, Throwable ex) {
        log.warn("Vendor service unavailable for operational-state batch lookup. Falling back to empty map (all vendors treated as visible).", ex);
        return Map.of();
    }

    private URI buildUri(String path) {
        return URI.create("http://vendor-service" + path);
    }
}
