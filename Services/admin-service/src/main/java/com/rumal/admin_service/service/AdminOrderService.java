package com.rumal.admin_service.service;

import com.rumal.admin_service.client.OrderClient;
import com.rumal.admin_service.dto.OrderResponse;
import com.rumal.admin_service.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminOrderService {

    private final OrderClient orderClient;

    @Cacheable(
            cacheNames = "adminOrders",
            key = "(#customerId == null ? 'ALL' : #customerId.toString()) + '::' + (#customerEmail == null ? 'NO_EMAIL' : #customerEmail) + '::' + #page + '::' + #size + '::' + #sort.toString()"
    )
    public PageResponse<OrderResponse> listOrders(UUID customerId, String customerEmail, int page, int size, List<String> sort, String internalAuth) {
        return orderClient.listOrders(customerId, customerEmail, page, size, sort, internalAuth);
    }
}
