package com.rumal.admin_service.service;

import com.rumal.admin_service.client.OrderClient;
import com.rumal.admin_service.dto.OrderResponse;
import com.rumal.admin_service.dto.OrderStatusAuditResponse;
import com.rumal.admin_service.dto.PageResponse;
import com.rumal.admin_service.dto.VendorOrderResponse;
import com.rumal.admin_service.dto.VendorOrderStatusAuditResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminOrderService {

    private final OrderClient orderClient;

    @Cacheable(
            cacheNames = "adminOrders",
            key = "(#customerId == null ? 'ALL' : #customerId.toString()) + '::' + (#customerEmail == null ? 'NO_EMAIL' : #customerEmail) + '::' + (#vendorId == null ? 'NO_VENDOR' : #vendorId.toString()) + '::' + #page + '::' + #size + '::' + #sort.toString()"
    )
    public PageResponse<OrderResponse> listOrders(UUID customerId, String customerEmail, UUID vendorId, int page, int size, List<String> sort, String internalAuth) {
        return orderClient.listOrders(customerId, customerEmail, vendorId, page, size, sort, internalAuth);
    }

    public OrderResponse updateOrderStatus(UUID orderId, String status, String internalAuth) {
        return orderClient.updateOrderStatus(orderId, status, internalAuth, null, null);
    }

    public OrderResponse updateOrderStatus(UUID orderId, String status, String internalAuth, String userSub, String userRoles) {
        return orderClient.updateOrderStatus(orderId, status, internalAuth, userSub, userRoles);
    }

    public Set<UUID> getOrderVendorIds(UUID orderId, String internalAuth) {
        return orderClient.getOrderVendorIds(orderId, internalAuth);
    }

    public List<OrderStatusAuditResponse> getOrderStatusHistory(UUID orderId, String internalAuth) {
        return orderClient.getOrderStatusHistory(orderId, internalAuth);
    }

    public List<VendorOrderResponse> getVendorOrders(UUID orderId, String internalAuth) {
        return orderClient.getVendorOrders(orderId, internalAuth);
    }

    public VendorOrderResponse getVendorOrder(UUID vendorOrderId, String internalAuth) {
        return orderClient.getVendorOrder(vendorOrderId, internalAuth);
    }

    public VendorOrderResponse updateVendorOrderStatus(UUID vendorOrderId, String status, String internalAuth, String userSub, String userRoles) {
        return orderClient.updateVendorOrderStatus(vendorOrderId, status, internalAuth, userSub, userRoles);
    }

    public List<VendorOrderStatusAuditResponse> getVendorOrderStatusHistory(UUID vendorOrderId, String internalAuth) {
        return orderClient.getVendorOrderStatusHistory(vendorOrderId, internalAuth);
    }
}
