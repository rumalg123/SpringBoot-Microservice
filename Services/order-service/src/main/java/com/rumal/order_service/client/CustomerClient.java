package com.rumal.order_service.client;

import com.rumal.order_service.dto.CustomerSummary;
import com.rumal.order_service.exception.ResourceNotFoundException;
import com.rumal.order_service.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class CustomerClient {

    private final RestClient.Builder lbRestClientBuilder;

    public CustomerClient(
            @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lbRestClientBuilder
    ) {
        this.lbRestClientBuilder = lbRestClientBuilder;
    }

    @Retry(name = "customerService")
    @CircuitBreaker(name = "customerService", fallbackMethod = "customerFallback")
    public void assertCustomerExists(UUID customerId) {
        RestClient rc = lbRestClientBuilder.build();

        try {
            rc.get()
                    .uri("http://customer-service/customers/{id}", customerId)
                    .retrieve()
                    .toBodilessEntity();

        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResourceNotFoundException("Customer not found: " + customerId);
            }
            throw ex;
        }
    }

    @Retry(name = "customerService")
    @CircuitBreaker(name = "customerService", fallbackMethod = "customerFallbackGetCustomer")
    public CustomerSummary getCustomer(UUID customerId) {
        RestClient rc = lbRestClientBuilder.build();

        try {
            return rc.get()
                    .uri("http://customer-service/customers/{id}", customerId)
                    .retrieve()
                    .body(CustomerSummary.class);
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResourceNotFoundException("Customer not found: " + customerId);
            }
            throw ex;
        }
    }

    @Retry(name = "customerService")
    @CircuitBreaker(name = "customerService", fallbackMethod = "customerFallbackGetCustomerByAuth0")
    public CustomerSummary getCustomerByAuth0Id(String auth0Id) {
        RestClient rc = lbRestClientBuilder.build();

        try {
            return rc.get()
                    .uri("http://customer-service/customers/me")
                    .header("X-Auth0-Sub", auth0Id)
                    .retrieve()
                    .body(CustomerSummary.class);
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResourceNotFoundException("Customer not found for auth0 id");
            }
            throw ex;
        }
    }



    public void customerFallback(UUID customerId, Throwable ex) {
        throw new ServiceUnavailableException("Customer service unavailable. Try again later.", ex);
    }

    public CustomerSummary customerFallbackGetCustomer(UUID customerId, Throwable ex) {
        throw new ServiceUnavailableException("Customer service unavailable. Try again later.", ex);
    }

    public CustomerSummary customerFallbackGetCustomerByAuth0(String auth0Id, Throwable ex) {
        throw new ServiceUnavailableException("Customer service unavailable. Try again later.", ex);
    }
}
