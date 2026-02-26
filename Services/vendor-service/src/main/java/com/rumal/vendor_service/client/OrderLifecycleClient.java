package com.rumal.vendor_service.client;

import com.rumal.vendor_service.dto.VendorOrderDeletionCheckResponse;
import com.rumal.vendor_service.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;

@Component
public class OrderLifecycleClient {

    private final RestClient restClient;

    public OrderLifecycleClient(@Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lbRestClientBuilder) {
        this.restClient = lbRestClientBuilder.build();
    }

    @Retry(name = "orderService")
    @CircuitBreaker(name = "orderService", fallbackMethod = "fallbackGetVendorDeletionCheck")
    public VendorOrderDeletionCheckResponse getVendorDeletionCheck(UUID vendorId, int refundHoldDays, String internalAuth) {
        RestClient rc = restClient;
        try {
            VendorOrderDeletionCheckResponse body = rc.get()
                    .uri("http://order-service/internal/orders/vendors/{vendorId}/deletion-check?refundHoldDays={refundHoldDays}",
                            vendorId, refundHoldDays)
                    .header("X-Internal-Auth", internalAuth)
                    .retrieve()
                    .body(VendorOrderDeletionCheckResponse.class);
            if (body == null) {
                throw new ServiceUnavailableException("Order service returned empty deletion check response");
            }
            return body;
        } catch (RestClientResponseException ex) {
            throw new ServiceUnavailableException("Order service deletion check failed (" + ex.getStatusCode().value() + ")", ex);
        } catch (RestClientException | IllegalStateException ex) {
            throw new ServiceUnavailableException("Order service unavailable for vendor deletion check", ex);
        }
    }

    @SuppressWarnings("unused")
    public VendorOrderDeletionCheckResponse fallbackGetVendorDeletionCheck(
            UUID vendorId,
            int refundHoldDays,
            String internalAuth,
            Throwable ex
    ) {
        throw new ServiceUnavailableException("Order service unavailable for vendor deletion check. Try again later.", ex);
    }
}
