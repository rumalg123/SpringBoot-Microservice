package com.rumal.admin_service.service;

import com.rumal.admin_service.client.OrderClient;
import com.rumal.admin_service.dto.OrderResponse;
import com.rumal.admin_service.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminOrderService {

    private final OrderClient orderClient;

    public PageResponse<OrderResponse> listOrders(UUID customerId, Pageable pageable, String internalAuth) {
        return orderClient.listOrders(customerId, pageable, internalAuth);
    }
}
