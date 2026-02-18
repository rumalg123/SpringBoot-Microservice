package com.rumal.admin_service.client;

import com.rumal.admin_service.dto.OrderResponse;
import com.rumal.admin_service.dto.PageResponse;
import com.rumal.admin_service.exception.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Component
public class OrderClient {

    private static final ParameterizedTypeReference<PageResponse<OrderResponse>> PAGE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient.Builder lbRestClientBuilder;

    public OrderClient(@Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lbRestClientBuilder) {
        this.lbRestClientBuilder = lbRestClientBuilder;
    }

    public PageResponse<OrderResponse> listOrders(UUID customerId, int page, int size, List<String> sort, String internalAuth) {
        RestClient rc = lbRestClientBuilder.build();

        try {
            PageResponse<OrderResponse> response = rc.get()
                    .uri(uriBuilder -> buildListOrdersUri(uriBuilder, customerId, page, size, sort))
                    .header("X-Internal-Auth", internalAuth)
                    .retrieve()
                    .body(PAGE_TYPE);

            if (response == null) {
                throw new ServiceUnavailableException("Order service returned an empty response", null);
            }
            return response;
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("Order service unavailable. Try again later.", ex);
        }
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
