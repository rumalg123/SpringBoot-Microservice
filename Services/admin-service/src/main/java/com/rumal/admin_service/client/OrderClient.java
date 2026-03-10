package com.rumal.admin_service.client;

import com.rumal.admin_service.dto.CreateOrderExportRequest;
import com.rumal.admin_service.dto.OrderResponse;
import com.rumal.admin_service.dto.OrderExportJobResponse;
import com.rumal.admin_service.dto.OrderListRequest;
import com.rumal.admin_service.dto.OrderStatusAuditResponse;
import com.rumal.admin_service.dto.PageResponse;
import com.rumal.admin_service.dto.UpdateOrderNoteRequest;
import com.rumal.admin_service.dto.UpdateOrderStatusRequest;
import com.rumal.admin_service.dto.VendorOrderResponse;
import com.rumal.admin_service.dto.VendorOrderStatusAuditResponse;
import com.rumal.admin_service.exception.DownstreamHttpException;
import com.rumal.admin_service.exception.ServiceUnavailableException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

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
    private static final String INTERNAL_AUTH_HEADER = "X-Internal-Auth";
    private static final String VENDOR_ID_FIELD = "vendorId";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;
    private final RetryRegistry retryRegistry;

    public OrderClient(
            @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lbRestClientBuilder,
            ObjectMapper objectMapper,
            CircuitBreakerFactory<?, ?> circuitBreakerFactory,
            RetryRegistry retryRegistry
    ) {
        this.restClient = lbRestClientBuilder.build();
        this.objectMapper = objectMapper;
        this.circuitBreakerFactory = circuitBreakerFactory;
        this.retryRegistry = retryRegistry;
    }

    public PageResponse<OrderResponse> listOrders(OrderListRequest request, String internalAuth) {
        return runOrderCall(() -> {
            RestClient rc = restClient;
            try {
                Map<String, Object> rawResponse = rc.get()
                        .uri(uriBuilder -> buildListOrdersUri(uriBuilder, request))
                        .header(INTERNAL_AUTH_HEADER, internalAuth)
                        .retrieve()
                        .body(MAP_TYPE);

                if (rawResponse == null) {
                    throw new ServiceUnavailableException("Order service returned an empty response", null);
                }
                return toPageResponse(rawResponse);
            } catch (RestClientException ex) {
                throw new ServiceUnavailableException("Order service unavailable. Try again later.", ex);
            }
        });
    }

    public OrderResponse updateOrderStatus(UUID orderId, String status, String internalAuth, String userSub, String userRoles) {
        return updateOrderStatus(orderId, status, internalAuth, userSub, userRoles, null);
    }

    public OrderResponse updateOrderStatus(
            UUID orderId,
            String status,
            String internalAuth,
            String userSub,
            String userRoles,
            String idempotencyKey
    ) {
        return runOrderCall(() -> {
            RestClient rc = restClient;
            try {
                RestClient.RequestBodySpec req = applyIdempotencyHeader(
                        applyActorHeaders(
                                rc.patch()
                                        .uri("http://order-service/orders/{id}/status", orderId)
                                        .header(INTERNAL_AUTH_HEADER, internalAuth),
                                userSub,
                                userRoles
                        ),
                        idempotencyKey
                );
                return req
                        .body(new UpdateOrderStatusRequest(status))
                        .retrieve()
                        .body(OrderResponse.class);
            } catch (RestClientResponseException ex) {
                throw toDownstreamHttpException(ex);
            } catch (RestClientException ex) {
                throw new ServiceUnavailableException("Order service unavailable. Try again later.", ex);
            }
        });
    }

    public OrderResponse updateOrderNote(UUID orderId, UpdateOrderNoteRequest body, String internalAuth, String idempotencyKey) {
        return runOrderCall(() -> {
            RestClient rc = restClient;
            try {
                RestClient.RequestBodySpec req = applyIdempotencyHeader(
                        rc.patch()
                                .uri("http://order-service/orders/{id}/note", orderId)
                                .header(INTERNAL_AUTH_HEADER, internalAuth),
                        idempotencyKey
                );
                return req
                        .body(body)
                        .retrieve()
                        .body(OrderResponse.class);
            } catch (RestClientResponseException ex) {
                throw toDownstreamHttpException(ex);
            } catch (RestClientException ex) {
                throw new ServiceUnavailableException("Order service unavailable. Try again later.", ex);
            }
        });
    }

    public List<OrderStatusAuditResponse> getOrderStatusHistory(UUID orderId, String internalAuth) {
        return runOrderCall(() -> {
            RestClient rc = restClient;
            try {
                List<OrderStatusAuditResponse> rows = rc.get()
                        .uri("http://order-service/orders/{id}/status-history", orderId)
                        .header(INTERNAL_AUTH_HEADER, internalAuth)
                        .retrieve()
                        .body(ORDER_STATUS_AUDIT_LIST_TYPE);
                return rows == null ? List.of() : rows;
            } catch (RestClientException ex) {
                throw new ServiceUnavailableException("Order service unavailable. Try again later.", ex);
            }
        });
    }

    public List<VendorOrderResponse> getVendorOrders(UUID orderId, String internalAuth) {
        return runOrderCall(() -> {
            RestClient rc = restClient;
            try {
                List<VendorOrderResponse> rows = rc.get()
                        .uri("http://order-service/orders/{id}/vendor-orders", orderId)
                        .header(INTERNAL_AUTH_HEADER, internalAuth)
                        .retrieve()
                        .body(VENDOR_ORDER_LIST_TYPE);
                return rows == null ? List.of() : rows;
            } catch (RestClientException ex) {
                throw new ServiceUnavailableException("Order service unavailable. Try again later.", ex);
            }
        });
    }

    public VendorOrderResponse getVendorOrder(UUID vendorOrderId, String internalAuth) {
        return runOrderCall(() -> {
            RestClient rc = restClient;
            try {
                return rc.get()
                        .uri("http://order-service/orders/vendor-orders/{id}", vendorOrderId)
                        .header(INTERNAL_AUTH_HEADER, internalAuth)
                        .retrieve()
                        .body(VendorOrderResponse.class);
            } catch (RestClientException ex) {
                throw new ServiceUnavailableException("Order service unavailable. Try again later.", ex);
            }
        });
    }

    public VendorOrderResponse updateVendorOrderStatus(UUID vendorOrderId, String status, String internalAuth, String userSub, String userRoles) {
        return updateVendorOrderStatus(vendorOrderId, status, internalAuth, userSub, userRoles, null);
    }

    public VendorOrderResponse updateVendorOrderStatus(
            UUID vendorOrderId,
            String status,
            String internalAuth,
            String userSub,
            String userRoles,
            String idempotencyKey
    ) {
        return runOrderCall(() -> {
            RestClient rc = restClient;
            try {
                RestClient.RequestBodySpec req = applyIdempotencyHeader(
                        applyActorHeaders(
                                rc.patch()
                                        .uri("http://order-service/orders/vendor-orders/{id}/status", vendorOrderId)
                                        .header(INTERNAL_AUTH_HEADER, internalAuth),
                                userSub,
                                userRoles
                        ),
                        idempotencyKey
                );
                return req
                        .body(new UpdateOrderStatusRequest(status))
                        .retrieve()
                        .body(VendorOrderResponse.class);
            } catch (RestClientResponseException ex) {
                throw toDownstreamHttpException(ex);
            } catch (RestClientException ex) {
                throw new ServiceUnavailableException("Order service unavailable. Try again later.", ex);
            }
        });
    }

    public List<VendorOrderStatusAuditResponse> getVendorOrderStatusHistory(UUID vendorOrderId, String internalAuth) {
        return runOrderCall(() -> {
            RestClient rc = restClient;
            try {
                List<VendorOrderStatusAuditResponse> rows = rc.get()
                        .uri("http://order-service/orders/vendor-orders/{id}/status-history", vendorOrderId)
                        .header(INTERNAL_AUTH_HEADER, internalAuth)
                        .retrieve()
                        .body(VENDOR_ORDER_STATUS_AUDIT_LIST_TYPE);
                return rows == null ? List.of() : rows;
            } catch (RestClientException ex) {
                throw new ServiceUnavailableException("Order service unavailable. Try again later.", ex);
            }
        });
    }

    public OrderExportJobResponse createOrderExport(
            CreateOrderExportRequest request,
            String internalAuth,
            String userSub,
            String userRoles
    ) {
        return runOrderCall(() -> {
            RestClient rc = restClient;
            try {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("format", request.format());
                putIfNotNull(payload, "status", request.status());
                putIfNotNull(payload, "customerEmail", request.customerEmail());
                putIfNotNull(payload, "createdAfter", request.createdAfter());
                putIfNotNull(payload, "createdBefore", request.createdBefore());
                putIfNotNull(payload, VENDOR_ID_FIELD, request.vendorId());
                putIfHasText(payload, "requestedBy", userSub);
                putIfHasText(payload, "requestedRoles", userRoles);
                return rc.post()
                        .uri("http://order-service/orders/exports")
                        .header(INTERNAL_AUTH_HEADER, internalAuth)
                        .body(payload)
                        .retrieve()
                        .body(OrderExportJobResponse.class);
            } catch (RestClientException ex) {
                throw new ServiceUnavailableException("Order service unavailable. Try again later.", ex);
            }
        });
    }

    public OrderExportJobResponse getOrderExportJob(UUID jobId, String internalAuth) {
        return runOrderCall(() -> {
            RestClient rc = restClient;
            try {
                return rc.get()
                        .uri("http://order-service/orders/exports/{id}", jobId)
                        .header(INTERNAL_AUTH_HEADER, internalAuth)
                        .retrieve()
                        .body(OrderExportJobResponse.class);
            } catch (RestClientException ex) {
                throw new ServiceUnavailableException("Order service unavailable. Try again later.", ex);
            }
        });
    }

    public ResponseEntity<byte[]> downloadOrderExport(UUID jobId, String internalAuth) {
        return runOrderCall(() -> {
            RestClient rc = restClient;
            try {
                return rc.get()
                        .uri("http://order-service/orders/exports/{id}/download", jobId)
                        .header(INTERNAL_AUTH_HEADER, internalAuth)
                        .retrieve()
                        .toEntity(byte[].class);
            } catch (RestClientException ex) {
                throw new ServiceUnavailableException("Order service unavailable. Try again later.", ex);
            }
        });
    }

    public Set<UUID> getOrderVendorIds(UUID orderId, String internalAuth) {
        return runOrderCall(() -> {
            RestClient rc = restClient;
            try {
                Map<String, Object> raw = rc.get()
                        .uri("http://order-service/orders/{id}/details", orderId)
                        .header(INTERNAL_AUTH_HEADER, internalAuth)
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
                    UUID vendorId = extractVendorId(itemObj);
                    if (vendorId != null) {
                        vendorIds.add(vendorId);
                    }
                }
                return Set.copyOf(vendorIds);
            } catch (RestClientException ex) {
                throw new ServiceUnavailableException("Order service unavailable. Try again later.", ex);
            }
        });
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

    private URI buildListOrdersUri(UriBuilder uriBuilder, OrderListRequest request) {
        UriBuilder builder = uriBuilder
                .scheme("http")
                .host("order-service")
                .path("/orders")
                .queryParam("page", Math.max(request.page(), 0))
                .queryParam("size", Math.max(request.size(), 1));

        builder = queryParamIfNotNull(builder, "customerId", request.customerId());
        builder = queryParamIfHasText(builder, "customerEmail", request.customerEmail());
        builder = queryParamIfNotNull(builder, VENDOR_ID_FIELD, request.vendorId());
        builder = queryParamIfHasText(builder, "status", request.status());
        builder = queryParamIfNotNull(builder, "createdAfter", request.createdAfter());
        builder = queryParamIfNotNull(builder, "createdBefore", request.createdBefore());
        builder = querySortParams(builder, request.sort());

        return builder.build();
    }

    private RestClient.RequestBodySpec applyActorHeaders(RestClient.RequestBodySpec requestSpec, String userSub, String userRoles) {
        RestClient.RequestBodySpec next = requestSpec;
        if (StringUtils.hasText(userSub)) {
            next = next.header("X-User-Sub", userSub);
        }
        if (StringUtils.hasText(userRoles)) {
            next = next.header("X-User-Roles", userRoles);
        }
        return next;
    }

    private RestClient.RequestBodySpec applyIdempotencyHeader(RestClient.RequestBodySpec requestSpec, String idempotencyKey) {
        String resolvedKey = ClientRequestUtils.resolveIdempotencyKey(idempotencyKey);
        if (!StringUtils.hasText(resolvedKey)) {
            return requestSpec;
        }
        return requestSpec.header(ClientRequestUtils.IDEMPOTENCY_HEADER, resolvedKey);
    }

    private void putIfNotNull(Map<String, Object> payload, String key, Object value) {
        if (value != null) {
            payload.put(key, value);
        }
    }

    private void putIfHasText(Map<String, Object> payload, String key, String value) {
        if (StringUtils.hasText(value)) {
            payload.put(key, value.trim());
        }
    }

    private UUID extractVendorId(Object itemObj) {
        if (!(itemObj instanceof Map<?, ?> itemMap)) {
            return null;
        }
        Object rawVendorId = itemMap.get(VENDOR_ID_FIELD);
        if (rawVendorId == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(rawVendorId));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private UriBuilder queryParamIfNotNull(UriBuilder builder, String name, Object value) {
        if (value == null) {
            return builder;
        }
        return builder.queryParam(name, value);
    }

    private UriBuilder queryParamIfHasText(UriBuilder builder, String name, String value) {
        if (!StringUtils.hasText(value)) {
            return builder;
        }
        return builder.queryParam(name, value.trim());
    }

    private UriBuilder querySortParams(UriBuilder builder, List<String> sort) {
        if (sort == null || sort.isEmpty()) {
            return builder;
        }
        UriBuilder next = builder;
        for (String sortValue : sort) {
            if (StringUtils.hasText(sortValue)) {
                next = next.queryParam("sort", sortValue.trim());
            }
        }
        return next;
    }

    private DownstreamHttpException toDownstreamHttpException(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        String message;
        if (StringUtils.hasText(body)) {
            String compactBody = body.replaceAll("\\s+", " ").trim();
            if (compactBody.length() > 300) {
                compactBody = compactBody.substring(0, 300) + "...";
            }
            message = "Order service responded with " + ex.getStatusCode().value() + ": " + compactBody;
        } else {
            message = "Order service responded with " + ex.getStatusCode().value();
        }
        return new DownstreamHttpException(ex.getStatusCode(), message, ex);
    }

    private <T> T runOrderCall(Supplier<T> action) {
        var retry = retryRegistry.retry("admin-order-client");
        Supplier<T> retryableAction = io.github.resilience4j.retry.Retry.decorateSupplier(retry, () ->
                circuitBreakerFactory.create("admin-order-client")
                        .run(action::get, throwable -> {
                            if (throwable instanceof RuntimeException re) {
                                throw re;
                            }
                            throw new ServiceUnavailableException("Order service unavailable. Try again later.", throwable);
                        })
        );
        return retryableAction.get();
    }
}
