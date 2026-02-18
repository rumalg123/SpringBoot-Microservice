package com.rumal.admin_service.client;

import com.rumal.admin_service.dto.OrderResponse;
import com.rumal.admin_service.dto.PageResponse;
import com.rumal.admin_service.exception.ServiceUnavailableException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
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

    public PageResponse<OrderResponse> listOrders(UUID customerId, Pageable pageable, String internalAuth) {
        RestClient rc = lbRestClientBuilder.build();

        try {
            PageResponse<OrderResponse> response = rc.get()
                    .uri(uriBuilder -> buildListOrdersUri(uriBuilder, customerId, pageable))
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

    private URI buildListOrdersUri(UriBuilder uriBuilder, UUID customerId, Pageable pageable) {
        UriBuilder builder = uriBuilder
                .scheme("http")
                .host("order-service")
                .path("/orders")
                .queryParam("page", pageable.getPageNumber())
                .queryParam("size", pageable.getPageSize());

        if (customerId != null) {
            builder = builder.queryParam("customerId", customerId);
        }

        if (pageable.getSort().isSorted()) {
            for (Sort.Order order : pageable.getSort()) {
                builder = builder.queryParam("sort", order.getProperty() + "," + order.getDirection().name());
            }
        }

        return builder.build();
    }
}
