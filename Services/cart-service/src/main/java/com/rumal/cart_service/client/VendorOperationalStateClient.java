package com.rumal.cart_service.client;

import com.rumal.cart_service.dto.VendorOperationalStateResponse;
import com.rumal.cart_service.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Component
public class VendorOperationalStateClient {

    private static final Logger log = LoggerFactory.getLogger(VendorOperationalStateClient.class);

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
    @CircuitBreaker(name = "vendorService", fallbackMethod = "fallbackGetState")
    public VendorOperationalStateResponse getState(UUID vendorId) {
        try {
            return lbRestClientBuilder.build()
                    .get()
                    .uri("http://vendor-service/internal/vendors/access/operational-state/{vendorId}", vendorId)
                    .header("X-Internal-Auth", internalAuthSharedSecret)
                    .retrieve()
                    .body(VendorOperationalStateResponse.class);
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Vendor service unavailable while validating vendor state.", ex);
        }
    }

    @SuppressWarnings("unused")
    public VendorOperationalStateResponse fallbackGetState(UUID vendorId, Throwable ex) {
        log.warn("Vendor service unavailable for vendor {}. Falling back to allow cart operation.", vendorId, ex);
        return new VendorOperationalStateResponse(vendorId, true, false, "ACTIVE", true, true);
    }
}
