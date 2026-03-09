package com.rumal.payment_service.client;

import com.rumal.payment_service.dto.OrderSummary;
import com.rumal.payment_service.dto.VendorOrderSummary;
import com.rumal.payment_service.dto.VendorOrderStatusHistoryEntry;
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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class OrderClient {

    private final RestClient restClient;
    private final String internalSharedSecret;

    public OrderClient(
            @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lbRestClientBuilder,
            @Value("${internal.auth.shared-secret:}") String internalSharedSecret
    ) {
        this.restClient = lbRestClientBuilder.build();
        this.internalSharedSecret = internalSharedSecret;
    }

    @Retry(name = "orderService")
    @CircuitBreaker(name = "orderService", fallbackMethod = "getOrderFallback")
    public OrderSummary getOrder(UUID orderId) {
        try {
            return restClient
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

            restClient
                    .patch()
                    .uri("http://order-service/orders/{id}/payment", orderId)
                    .header("X-Internal-Auth", internalSharedSecret)
                    .header("Idempotency-Key", buildIdempotencyKey(
                            "set-payment-info",
                            orderId,
                            paymentId + "|" + paymentMethod + "|" + String.valueOf(paymentGatewayRef)
                    ))
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

            restClient
                    .patch()
                    .uri("http://order-service/orders/{id}/status", orderId)
                    .header("X-Internal-Auth", internalSharedSecret)
                    .header("Idempotency-Key", buildIdempotencyKey(
                            "update-order-status",
                            orderId,
                            status + "|" + String.valueOf(reason)
                    ))
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
            VendorOrderSummary vendorOrder = restClient
                    .get()
                    .uri("http://order-service/orders/vendor-orders/{vendorOrderId}", vendorOrderId)
                    .header("X-Internal-Auth", internalSharedSecret)
                    .retrieve()
                    .body(VendorOrderSummary.class);
            if (vendorOrder == null) {
                throw new ResourceNotFoundException("Vendor order not found: " + vendorOrderId);
            }
            if (vendorOrder.orderId() != null && !vendorOrder.orderId().equals(orderId)) {
                throw new ResourceNotFoundException("Vendor order not found under order: " + orderId);
            }
            return vendorOrder;
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
        updateVendorOrderStatus(orderId, vendorOrderId, status, reason, null, null, null);
    }

    @Retry(name = "orderService")
    @CircuitBreaker(name = "orderService", fallbackMethod = "updateVendorOrderStatusWithRefundFallback")
    public void updateVendorOrderStatus(
            UUID orderId,
            UUID vendorOrderId,
            String status,
            String reason,
            String refundReason,
            BigDecimal refundAmount,
            Integer refundedQuantity
    ) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", status);
            if (reason != null) {
                body.put("reason", reason);
            }
            if (refundReason != null) {
                body.put("refundReason", refundReason);
            }
            if (refundAmount != null) {
                body.put("refundAmount", refundAmount);
            }
            if (refundedQuantity != null) {
                body.put("refundedQuantity", refundedQuantity);
            }

            restClient
                    .patch()
                    .uri("http://order-service/orders/vendor-orders/{vendorOrderId}/status", vendorOrderId)
                    .header("X-Internal-Auth", internalSharedSecret)
                    .header("Idempotency-Key", buildIdempotencyKey(
                            "update-vendor-order-status",
                            vendorOrderId,
                            String.valueOf(orderId) + "|" + status + "|" + String.valueOf(reason)
                                    + "|" + String.valueOf(refundReason)
                                    + "|" + String.valueOf(refundAmount)
                                    + "|" + String.valueOf(refundedQuantity)
                    ))
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

    @Retry(name = "orderService")
    @CircuitBreaker(name = "orderService", fallbackMethod = "getVendorOrderStatusHistoryFallback")
    public List<VendorOrderStatusHistoryEntry> getVendorOrderStatusHistory(UUID vendorOrderId) {
        try {
            List<VendorOrderStatusHistoryEntry> history = restClient
                    .get()
                    .uri("http://order-service/orders/vendor-orders/{vendorOrderId}/status-history", vendorOrderId)
                    .header("X-Internal-Auth", internalSharedSecret)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<List<VendorOrderStatusHistoryEntry>>() {});
            return history == null ? List.of() : history;
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResourceNotFoundException("Vendor order not found: " + vendorOrderId);
            }
            throw ex;
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Order service unavailable: " + ex.getMessage(), ex);
        }
    }

    // H-07: Fetch vendor order by ID alone (no parent orderId needed) for payout validation
    @Retry(name = "orderService")
    @CircuitBreaker(name = "orderService", fallbackMethod = "getVendorOrderByIdFallback")
    public VendorOrderSummary getVendorOrderById(UUID vendorOrderId) {
        try {
            return restClient
                    .get()
                    .uri("http://order-service/orders/vendor-orders/{vendorOrderId}", vendorOrderId)
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

    @SuppressWarnings("unused")
    public VendorOrderSummary getVendorOrderByIdFallback(UUID vendorOrderId, Throwable ex) {
        if (ex instanceof ResourceNotFoundException rnfe) throw rnfe;
        throw new ServiceUnavailableException("Order service unavailable for vendor order " + vendorOrderId + ". Try again later.", ex);
    }

    // Fallback methods
    @SuppressWarnings("unused")
    public OrderSummary getOrderFallback(UUID orderId, Throwable ex) {
        if (ex instanceof ResourceNotFoundException rnfe) throw rnfe;
        throw new ServiceUnavailableException("Order service unavailable for order " + orderId + ". Try again later.", ex);
    }

    @SuppressWarnings("unused")
    public void setPaymentInfoFallback(UUID orderId, String paymentId, String paymentMethod, String paymentGatewayRef, Throwable ex) {
        if (ex instanceof ResourceNotFoundException rnfe) throw rnfe;
        throw new ServiceUnavailableException("Order service unavailable for setting payment info. Try again later.", ex);
    }

    @SuppressWarnings("unused")
    public void updateOrderStatusFallback(UUID orderId, String status, String reason, Throwable ex) {
        if (ex instanceof ResourceNotFoundException rnfe) throw rnfe;
        throw new ServiceUnavailableException("Order service unavailable for status update. Try again later.", ex);
    }

    @SuppressWarnings("unused")
    public VendorOrderSummary getVendorOrderFallback(UUID orderId, UUID vendorOrderId, Throwable ex) {
        if (ex instanceof ResourceNotFoundException rnfe) throw rnfe;
        throw new ServiceUnavailableException("Order service unavailable for vendor order " + vendorOrderId + ". Try again later.", ex);
    }

    @SuppressWarnings("unused")
    public void updateVendorOrderStatusFallback(UUID orderId, UUID vendorOrderId, String status, String reason, Throwable ex) {
        if (ex instanceof ResourceNotFoundException rnfe) throw rnfe;
        throw new ServiceUnavailableException("Order service unavailable for vendor order status update. Try again later.", ex);
    }

    @SuppressWarnings("unused")
    public void updateVendorOrderStatusWithRefundFallback(
            UUID orderId,
            UUID vendorOrderId,
            String status,
            String reason,
            String refundReason,
            BigDecimal refundAmount,
            Integer refundedQuantity,
            Throwable ex
    ) {
        if (ex instanceof ResourceNotFoundException rnfe) throw rnfe;
        throw new ServiceUnavailableException("Order service unavailable for vendor order status update. Try again later.", ex);
    }

    @SuppressWarnings("unused")
    public List<VendorOrderStatusHistoryEntry> getVendorOrderStatusHistoryFallback(UUID vendorOrderId, Throwable ex) {
        if (ex instanceof ResourceNotFoundException rnfe) throw rnfe;
        throw new ServiceUnavailableException("Order service unavailable for vendor order " + vendorOrderId + " history. Try again later.", ex);
    }

    private String buildIdempotencyKey(String action, UUID targetId, String payload) {
        String normalizedAction = normalizeAction(action);
        String raw = normalizedAction + "|" + String.valueOf(targetId) + "|" + String.valueOf(payload);
        String digest = sha256Hex(raw);
        // Keep key compact and within the gateway/order-service pattern and length constraints.
        return "ps-" + normalizedAction + "-" + digest.substring(0, 32);
    }

    private String normalizeAction(String value) {
        if (value == null || value.isBlank()) {
            return "mutate";
        }
        String normalized = value.trim().toLowerCase()
                .replaceAll("[^a-z0-9\\-_]", "-")
                .replaceAll("-{2,}", "-");
        if (normalized.isBlank()) {
            return "mutate";
        }
        return normalized.length() > 24 ? normalized.substring(0, 24) : normalized;
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8))
                    .toString()
                    .replace("-", "");
        }
    }
}
