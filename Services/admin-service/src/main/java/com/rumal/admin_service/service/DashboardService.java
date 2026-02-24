package com.rumal.admin_service.service;

import com.rumal.admin_service.client.OrderClient;
import com.rumal.admin_service.dto.DashboardSummaryResponse;
import com.rumal.admin_service.dto.OrderResponse;
import com.rumal.admin_service.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);
    private final OrderClient orderClient;

    @Cacheable(cacheNames = "dashboardSummary", key = "'global'")
    public DashboardSummaryResponse getSummary(String internalAuth) {
        Map<String, Long> ordersByStatus = new LinkedHashMap<>();
        long totalOrders = 0;
        long pendingOrders = 0;
        long processingOrders = 0;
        long completedOrders = 0;
        long cancelledOrders = 0;

        for (String status : List.of("PENDING_PAYMENT", "PROCESSING", "SHIPPED", "DELIVERED", "CANCELLED", "REFUNDED")) {
            try {
                PageResponse<OrderResponse> page = orderClient.listOrders(
                        null, null, null, status, null, null, 0, 1, List.of("createdAt,DESC"), internalAuth);
                long count = page != null ? page.totalElements() : 0;
                ordersByStatus.put(status, count);
                totalOrders += count;
                switch (status) {
                    case "PENDING_PAYMENT" -> pendingOrders = count;
                    case "PROCESSING", "SHIPPED" -> processingOrders += count;
                    case "DELIVERED" -> completedOrders = count;
                    case "CANCELLED" -> cancelledOrders = count;
                }
            } catch (Exception e) {
                log.warn("Failed to fetch order count for status={}", status, e);
                ordersByStatus.put(status, 0L);
            }
        }

        return new DashboardSummaryResponse(
                totalOrders, pendingOrders, processingOrders, completedOrders, cancelledOrders,
                0, 0, 0, 0,
                ordersByStatus, Instant.now()
        );
    }
}
