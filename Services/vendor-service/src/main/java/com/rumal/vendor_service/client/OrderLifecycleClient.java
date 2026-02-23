package com.rumal.vendor_service.client;

import com.rumal.vendor_service.dto.VendorOrderDeletionCheckResponse;
import com.rumal.vendor_service.exception.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;

@Component
public class OrderLifecycleClient {

    private final RestClient.Builder lbRestClientBuilder;

    public OrderLifecycleClient(@Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lbRestClientBuilder) {
        this.lbRestClientBuilder = lbRestClientBuilder;
    }

    public VendorOrderDeletionCheckResponse getVendorDeletionCheck(UUID vendorId, int refundHoldDays, String internalAuth) {
        RestClient rc = lbRestClientBuilder.build();
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
}
