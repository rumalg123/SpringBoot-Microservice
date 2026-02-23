package com.rumal.admin_service.client;

import com.rumal.admin_service.dto.OrderResponse;
import com.rumal.admin_service.dto.OrderStatusAuditResponse;
import com.rumal.admin_service.dto.PageResponse;
import com.rumal.admin_service.dto.UpdateOrderStatusRequest;
import com.rumal.admin_service.dto.VendorOrderResponse;
import com.rumal.admin_service.dto.VendorOrderStatusAuditResponse;
import com.rumal.admin_service.exception.ServiceUnavailableException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class OrderClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<OrderStatusAuditResponse>> ORDER_STATUS_AUDIT_LIST_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<VendorOrderResponse>> VENDOR_ORDER_LIST_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<VendorOrderStatusAuditResponse>> VENDOR_ORDER_STATUS_AUDIT_LIST_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient.Builder lbRestClientBuilder;
    private final ObjectMapper objectMapper;

    public OrderClient(
            @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lbRestClientBuilder,
            ObjectMapper objectMapper
    ) {
        this.lbRestClientBuilder = lbRestClientBuilder;
        this.objectMapper = objectMapper;
    }

    public PageResponse<OrderResponse> listOrders(UUID customerId, String customerEmail, UUID vendorId, int page, int size, List<String> sort, String internalAuth) {
        RestClient rc = lbRestClientBuilder.build();

        try {
            Map<String, Object> rawResponse = rc.get()
                    .uri(uriBuilder -> buildListOrdersUri(uriBuilder, customerId, customerEmail, vendorId, page, size, sort))
                    .header("X-Internal-Auth", internalAuth)
                    .retrieve()
                    .body(MAP_TYPE);

            if (rawResponse == null) {
                throw new ServiceUnavailableException("Order service returned an empty response", null);
            }
            return toPageResponse(rawResponse);
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Order service unavailable. Try again later.", ex);
        }
    }

    public OrderResponse updateOrderStatus(UUID orderId, String status, String internalAuth, String userSub, String userRoles) {
        RestClient rc = lbRestClientBuilder.build();
        try {
            var req = rc.patch()
                    .uri("http://order-service/orders/{id}/status", orderId)
                    .header("X-Internal-Auth", internalAuth);
            if (userSub != null && !userSub.isBlank()) {
                req = req.header("X-User-Sub", userSub);
            }
            if (userRoles != null && !userRoles.isBlank()) {
                req = req.header("X-User-Roles", userRoles);
            }
            return req.body(new UpdateOrderStatusRequest(status))
                    .retrieve()
                    .body(OrderResponse.class);
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Order service unavailable. Try again later.", ex);
        }
    }

    public List<OrderStatusAuditResponse> getOrderStatusHistory(UUID orderId, String internalAuth) {
        RestClient rc = lbRestClientBuilder.build();
        try {
            List<OrderStatusAuditResponse> rows = rc.get()
                    .uri("http://order-service/orders/{id}/status-history", orderId)
                    .header("X-Internal-Auth", internalAuth)
                    .retrieve()
                    .body(ORDER_STATUS_AUDIT_LIST_TYPE);
            return rows == null ? List.of() : rows;
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Order service unavailable. Try again later.", ex);
        }
    }

    public List<VendorOrderResponse> getVendorOrders(UUID orderId, String internalAuth) {
        RestClient rc = lbRestClientBuilder.build();
        try {
            List<VendorOrderResponse> rows = rc.get()
                    .uri("http://order-service/orders/{id}/vendor-orders", orderId)
                    .header("X-Internal-Auth", internalAuth)
                    .retrieve()
                    .body(VENDOR_ORDER_LIST_TYPE);
            return rows == null ? List.of() : rows;
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Order service unavailable. Try again later.", ex);
        }
    }

    public VendorOrderResponse getVendorOrder(UUID vendorOrderId, String internalAuth) {
        RestClient rc = lbRestClientBuilder.build();
        try {
            return rc.get()
                    .uri("http://order-service/orders/vendor-orders/{id}", vendorOrderId)
                    .header("X-Internal-Auth", internalAuth)
                    .retrieve()
                    .body(VendorOrderResponse.class);
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Order service unavailable. Try again later.", ex);
        }
    }

    public VendorOrderResponse updateVendorOrderStatus(UUID vendorOrderId, String status, String internalAuth, String userSub, String userRoles) {
        RestClient rc = lbRestClientBuilder.build();
        try {
            var req = rc.patch()
                    .uri("http://order-service/orders/vendor-orders/{id}/status", vendorOrderId)
                    .header("X-Internal-Auth", internalAuth);
            if (userSub != null && !userSub.isBlank()) {
                req = req.header("X-User-Sub", userSub);
            }
            if (userRoles != null && !userRoles.isBlank()) {
                req = req.header("X-User-Roles", userRoles);
            }
            return req.body(new UpdateOrderStatusRequest(status))
                    .retrieve()
                    .body(VendorOrderResponse.class);
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Order service unavailable. Try again later.", ex);
        }
    }

    public List<VendorOrderStatusAuditResponse> getVendorOrderStatusHistory(UUID vendorOrderId, String internalAuth) {
        RestClient rc = lbRestClientBuilder.build();
        try {
            List<VendorOrderStatusAuditResponse> rows = rc.get()
                    .uri("http://order-service/orders/vendor-orders/{id}/status-history", vendorOrderId)
                    .header("X-Internal-Auth", internalAuth)
                    .retrieve()
                    .body(VENDOR_ORDER_STATUS_AUDIT_LIST_TYPE);
            return rows == null ? List.of() : rows;
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Order service unavailable. Try again later.", ex);
        }
    }

    public Set<UUID> getOrderVendorIds(UUID orderId, String internalAuth) {
        RestClient rc = lbRestClientBuilder.build();
        try {
            Map<String, Object> raw = rc.get()
                    .uri("http://order-service/orders/{id}/details", orderId)
                    .header("X-Internal-Auth", internalAuth)
                    .retrieve()
                    .body(MAP_TYPE);
            if (raw == null) {
                throw new ServiceUnavailableException("Order service returned an empty response", null);
            }
            Object rawItems = raw.get("items");
            if (!(rawItems instanceof List<?> items)) {
                return Set.of();
            }
            Set<UUID> vendorIds = new LinkedHashSet<>();
            for (Object itemObj : items) {
                if (!(itemObj instanceof Map<?, ?> itemMap)) {
                    continue;
                }
                Object rawVendorId = itemMap.get("vendorId");
                if (rawVendorId == null) {
                    continue;
                }
                try {
                    vendorIds.add(UUID.fromString(String.valueOf(rawVendorId)));
                } catch (IllegalArgumentException ignored) {
                    // ignore malformed vendor id
                }
            }
            return Set.copyOf(vendorIds);
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Order service unavailable. Try again later.", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private PageResponse<OrderResponse> toPageResponse(Map<String, Object> raw) {
        List<OrderResponse> content = objectMapper.convertValue(
                raw.getOrDefault("content", Collections.emptyList()),
                new TypeReference<>() {}
        );

        Map<String, Object> page = raw.get("page") instanceof Map<?, ?> p
                ? (Map<String, Object>) p
                : Collections.emptyMap();

        int number = intValue(raw.get("number"), intValue(page.get("number"), 0));
        int size = intValue(raw.get("size"), intValue(page.get("size"), content.size()));
        long totalElements = longValue(raw.get("totalElements"), longValue(page.get("totalElements"), content.size()));
        int totalPages = intValue(raw.get("totalPages"), intValue(page.get("totalPages"), totalElements > 0 ? 1 : 0));
        boolean first = booleanValue(raw.get("first"), number <= 0);
        boolean last = booleanValue(raw.get("last"), totalPages <= 1 || number >= totalPages - 1);
        boolean empty = booleanValue(raw.get("empty"), content.isEmpty());

        return new PageResponse<>(content, number, size, totalElements, totalPages, first, last, empty);
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        return fallback;
    }

    private long longValue(Object value, long fallback) {
        if (value instanceof Number n) return n.longValue();
        return fallback;
    }

    private boolean booleanValue(Object value, boolean fallback) {
        if (value instanceof Boolean b) return b;
        return fallback;
    }

    private URI buildListOrdersUri(UriBuilder uriBuilder, UUID customerId, String customerEmail, UUID vendorId, int page, int size, List<String> sort) {
        UriBuilder builder = uriBuilder
                .scheme("http")
                .host("order-service")
                .path("/orders")
                .queryParam("page", Math.max(page, 0))
                .queryParam("size", Math.max(size, 1));

        if (customerId != null) {
            builder = builder.queryParam("customerId", customerId);
        }
        if (customerEmail != null && !customerEmail.isBlank()) {
            builder = builder.queryParam("customerEmail", customerEmail.trim());
        }
        if (vendorId != null) {
            builder = builder.queryParam("vendorId", vendorId);
        }

        if (sort != null && !sort.isEmpty()) {
            for (String sortValue : sort) {
                if (sortValue != null && !sortValue.isBlank()) {
                    builder = builder.queryParam("sort", sortValue);
                }
            }
        }

        return builder.build();
    }
}
