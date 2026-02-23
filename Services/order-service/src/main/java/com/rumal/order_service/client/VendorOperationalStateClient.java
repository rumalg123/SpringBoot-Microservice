package com.rumal.order_service.client;

import com.rumal.order_service.dto.VendorOperationalStateResponse;
import com.rumal.order_service.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.*;

@Component
public class VendorOperationalStateClient {

    private static final ParameterizedTypeReference<List<VendorOperationalStateResponse>> LIST_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient.Builder lbRestClientBuilder;
    private final String internalAuthSharedSecret;

    public VendorOperationalStateClient(
            @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lbRestClientBuilder,
            @Value("${internal.auth.shared-secret:}") String internalAuthSharedSecret
    ) {
        this.lbRestClientBuilder = lbRestClientBuilder;
        this.internalAuthSharedSecret = internalAuthSharedSecret == null ? "" : internalAuthSharedSecret.trim();
    }

    @Retry(name = "vendorService")
    @CircuitBreaker(name = "vendorService", fallbackMethod = "fallbackBatchGetStates")
    public Map<UUID, VendorOperationalStateResponse> batchGetStates(Collection<UUID> vendorIds) {
        if (vendorIds == null || vendorIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = vendorIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        try {
            List<VendorOperationalStateResponse> rows = lbRestClientBuilder.build()
                    .post()
                    .uri("http://vendor-service/internal/vendors/access/operational-state/batch")
                    .header("X-Internal-Auth", internalAuthSharedSecret)
                    .body(ids)
                    .retrieve()
                    .body(LIST_TYPE);

            Map<UUID, VendorOperationalStateResponse> out = new LinkedHashMap<>();
            if (rows != null) {
                for (VendorOperationalStateResponse row : rows) {
                    if (row != null && row.vendorId() != null) {
                        out.put(row.vendorId(), row);
                    }
                }
            }
            return out;
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Vendor service unavailable while validating vendor state.", ex);
        }
    }

    @SuppressWarnings("unused")
    public Map<UUID, VendorOperationalStateResponse> fallbackBatchGetStates(Collection<UUID> vendorIds, Throwable ex) {
        throw new ServiceUnavailableException("Vendor service unavailable while validating vendor state. Try again later.", ex);
    }
}
