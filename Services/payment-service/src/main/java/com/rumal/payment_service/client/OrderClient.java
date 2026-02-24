package com.rumal.payment_service.client;

import com.rumal.payment_service.dto.OrderSummary;
import com.rumal.payment_service.dto.VendorOrderSummary;
import com.rumal.payment_service.exception.ResourceNotFoundException;
import com.rumal.payment_service.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class OrderClient {

    private final RestClient.Builder lbRestClientBuilder;
    private final String internalSharedSecret;

    public OrderClient(
            @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lbRestClientBuilder,
            @Value("${internal.auth.shared-secret:}") String internalSharedSecret
    ) {
        this.lbRestClientBuilder = lbRestClientBuilder;
        this.internalSharedSecret = internalSharedSecret;
    }

    @Retry(name = "orderService")
    @CircuitBreaker(name = "orderService", fallbackMethod = "getOrderFallback")
    public OrderSummary getOrder(UUID orderId) {
        try {
            return lbRestClientBuilder.build()
                    .get()
                    .uri("http://order-service/orders/{id}", orderId)
                    .header("X-Internal-Auth", internalSharedSecret)
                    .retrieve()
                    .body(OrderSummary.class);
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResourceNotFoundException("Order not found: " + orderId);
            }
            throw ex;
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Order service unavailable: " + ex.getMessage(), ex);
        }
    }

    @Retry(name = "orderService")
    @CircuitBreaker(name = "orderService", fallbackMethod = "setPaymentInfoFallback")
    public void setPaymentInfo(UUID orderId, String paymentId, String paymentMethod, String paymentGatewayRef) {
        try {
            Map<String, String> body = new LinkedHashMap<>();
            body.put("paymentId", paymentId);
            body.put("paymentMethod", paymentMethod);
            if (paymentGatewayRef != null) {
                body.put("paymentGatewayRef", paymentGatewayRef);
            }

            lbRestClientBuilder.build()
                    .patch()
                    .uri("http://order-service/orders/{id}/payment", orderId)
                    .header("X-Internal-Auth", internalSharedSecret)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResourceNotFoundException("Order not found: " + orderId);
            }
            throw ex;
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Order service unavailable: " + ex.getMessage(), ex);
        }
    }

    @Retry(name = "orderService")
    @CircuitBreaker(name = "orderService", fallbackMethod = "updateOrderStatusFallback")
    public void updateOrderStatus(UUID orderId, String status, String reason) {
        try {
            Map<String, String> body = new LinkedHashMap<>();
            body.put("status", status);
            if (reason != null) {
                body.put("reason", reason);
            }

            lbRestClientBuilder.build()
                    .patch()
                    .uri("http://order-service/orders/{id}/status", orderId)
                    .header("X-Internal-Auth", internalSharedSecret)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResourceNotFoundException("Order not found: " + orderId);
            }
            throw ex;
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Order service unavailable: " + ex.getMessage(), ex);
        }
    }

    @Retry(name = "orderService")
    @CircuitBreaker(name = "orderService", fallbackMethod = "getVendorOrderFallback")
    public VendorOrderSummary getVendorOrder(UUID orderId, UUID vendorOrderId) {
        try {
            return lbRestClientBuilder.build()
                    .get()
                    .uri("http://order-service/orders/{orderId}/vendor-orders/{vendorOrderId}", orderId, vendorOrderId)
                    .header("X-Internal-Auth", internalSharedSecret)
                    .retrieve()
                    .body(VendorOrderSummary.class);
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResourceNotFoundException("Vendor order not found: " + vendorOrderId);
            }
            throw ex;
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Order service unavailable: " + ex.getMessage(), ex);
        }
    }

    @Retry(name = "orderService")
    @CircuitBreaker(name = "orderService", fallbackMethod = "updateVendorOrderStatusFallback")
    public void updateVendorOrderStatus(UUID orderId, UUID vendorOrderId, String status, String reason) {
        try {
            Map<String, String> body = new LinkedHashMap<>();
            body.put("status", status);
            if (reason != null) {
                body.put("reason", reason);
            }

            lbRestClientBuilder.build()
                    .patch()
                    .uri("http://order-service/orders/{orderId}/vendor-orders/{vendorOrderId}/status", orderId, vendorOrderId)
                    .header("X-Internal-Auth", internalSharedSecret)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResourceNotFoundException("Vendor order not found: " + vendorOrderId);
            }
            throw ex;
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Order service unavailable: " + ex.getMessage(), ex);
        }
    }

    // Fallback methods
    @SuppressWarnings("unused")
    public OrderSummary getOrderFallback(UUID orderId, Throwable ex) {
        throw new ServiceUnavailableException("Order service unavailable for order " + orderId + ". Try again later.", ex);
    }

    @SuppressWarnings("unused")
    public void setPaymentInfoFallback(UUID orderId, String paymentId, String paymentMethod, String paymentGatewayRef, Throwable ex) {
        throw new ServiceUnavailableException("Order service unavailable for setting payment info. Try again later.", ex);
    }

    @SuppressWarnings("unused")
    public void updateOrderStatusFallback(UUID orderId, String status, String reason, Throwable ex) {
        throw new ServiceUnavailableException("Order service unavailable for status update. Try again later.", ex);
    }

    @SuppressWarnings("unused")
    public VendorOrderSummary getVendorOrderFallback(UUID orderId, UUID vendorOrderId, Throwable ex) {
        throw new ServiceUnavailableException("Order service unavailable for vendor order " + vendorOrderId + ". Try again later.", ex);
    }

    @SuppressWarnings("unused")
    public void updateVendorOrderStatusFallback(UUID orderId, UUID vendorOrderId, String status, String reason, Throwable ex) {
        throw new ServiceUnavailableException("Order service unavailable for vendor order status update. Try again later.", ex);
    }
}
