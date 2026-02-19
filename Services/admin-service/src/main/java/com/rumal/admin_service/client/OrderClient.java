package com.rumal.admin_service.client;

import com.rumal.admin_service.dto.OrderResponse;
import com.rumal.admin_service.dto.PageResponse;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class OrderClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
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

    public PageResponse<OrderResponse> listOrders(UUID customerId, int page, int size, List<String> sort, String internalAuth) {
        RestClient rc = lbRestClientBuilder.build();

        try {
            Map<String, Object> rawResponse = rc.get()
                    .uri(uriBuilder -> buildListOrdersUri(uriBuilder, customerId, page, size, sort))
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

    private URI buildListOrdersUri(UriBuilder uriBuilder, UUID customerId, int page, int size, List<String> sort) {
        UriBuilder builder = uriBuilder
                .scheme("http")
                .host("order-service")
                .path("/orders")
                .queryParam("page", Math.max(page, 0))
                .queryParam("size", Math.max(size, 1));

        if (customerId != null) {
            builder = builder.queryParam("customerId", customerId);
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
