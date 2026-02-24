package com.rumal.order_service.client;

import com.rumal.order_service.dto.VendorSummaryForOrder;
import com.rumal.order_service.exception.ResourceNotFoundException;
import com.rumal.order_service.exception.ServiceUnavailableException;
import com.rumal.order_service.exception.ValidationException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Component
public class VendorClient {

    private final RestClient.Builder lbRestClientBuilder;
    private final String internalAuthSecret;

    public VendorClient(
            @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lbRestClientBuilder,
            @Value("${internal.auth.shared-secret:}") String internalAuthSecret
    ) {
        this.lbRestClientBuilder = lbRestClientBuilder;
        this.internalAuthSecret = internalAuthSecret;
    }

    @Retry(name = "vendorService")
    @CircuitBreaker(name = "vendorService", fallbackMethod = "vendorFallback")
    public VendorSummaryForOrder getVendorForUser(String userSub, UUID vendorIdHint) {
        RestClient rc = lbRestClientBuilder.build();
        try {
            String uri = vendorIdHint != null
                    ? "http://vendor-service/vendors/me?vendorId=" + vendorIdHint
                    : "http://vendor-service/vendors/me";
            return rc.get()
                    .uri(uri)
                    .header("X-User-Sub", userSub)
                    .header("X-Internal-Auth", internalAuthSecret)
                    .retrieve()
                    .body(VendorSummaryForOrder.class);
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResourceNotFoundException("No active vendor membership found for user");
            }
            if (ex.getStatusCode().value() == 422 || ex.getStatusCode() == HttpStatus.BAD_REQUEST) {
                throw new ValidationException("Vendor resolution failed: " + ex.getMessage());
            }
            throw ex;
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Vendor service unavailable: " + ex.getMessage(), ex);
        }
    }

    @SuppressWarnings("unused")
    public VendorSummaryForOrder vendorFallback(String userSub, UUID vendorIdHint, Throwable ex) {
        throw new ServiceUnavailableException("Vendor service unavailable. Try again later.", ex);
    }
}
