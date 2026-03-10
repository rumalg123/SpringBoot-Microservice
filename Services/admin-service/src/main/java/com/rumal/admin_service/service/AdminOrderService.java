package com.rumal.admin_service.service;

import com.rumal.admin_service.client.OrderClient;
import com.rumal.admin_service.dto.BulkOperationResult;
import com.rumal.admin_service.dto.CreateOrderExportRequest;
import com.rumal.admin_service.dto.OrderListRequest;
import com.rumal.admin_service.dto.OrderResponse;
import com.rumal.admin_service.dto.OrderExportJobResponse;
import com.rumal.admin_service.dto.OrderStatusAuditResponse;
import com.rumal.admin_service.dto.PageResponse;
import com.rumal.admin_service.dto.UpdateOrderNoteRequest;
import com.rumal.admin_service.dto.VendorOrderResponse;
import com.rumal.admin_service.dto.VendorOrderStatusAuditResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminOrderService {

    private static final Logger log = LoggerFactory.getLogger(AdminOrderService.class);

    private final OrderClient orderClient;
    private final AdminOrderCacheVersionService adminOrderCacheVersionService;

    @Cacheable(
            cacheNames = "adminOrders",
            key = "@adminOrderCacheVersionService.adminOrdersVersion() + '::' + "
                    + "(#request.customerId() == null ? 'ALL' : #request.customerId().toString()) + '::' + "
                    + "(#request.customerEmail() == null ? 'NO_EMAIL' : #request.customerEmail()) + '::' + "
                    + "(#request.vendorId() == null ? 'NO_VENDOR' : #request.vendorId().toString()) + '::' + "
                    + "(#request.status() == null ? 'ALL_STATUS' : #request.status()) + '::' + "
                    + "(#request.createdAfter() == null ? 'NO_AFTER' : #request.createdAfter().toString()) + '::' + "
                    + "(#request.createdBefore() == null ? 'NO_BEFORE' : #request.createdBefore().toString()) + '::' + "
                    + "#request.page() + '::' + #request.size() + '::' + #request.sort().toString()"
    )
    public PageResponse<OrderResponse> listOrders(OrderListRequest request, String internalAuth) {
        return orderClient.listOrders(request, internalAuth);
    }

    public OrderResponse updateOrderStatus(UUID orderId, String status, String internalAuth) {
        return updateOrderStatus(orderId, status, internalAuth, null, null);
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
        OrderResponse response = orderClient.updateOrderStatus(orderId, status, internalAuth, userSub, userRoles, idempotencyKey);
        adminOrderCacheVersionService.bumpAdminOrdersCache();
        return response;
    }

    public OrderResponse updateOrderNote(UUID orderId, UpdateOrderNoteRequest req, String internalAuth, String idempotencyKey) {
        OrderResponse response = orderClient.updateOrderNote(orderId, req, internalAuth, idempotencyKey);
        adminOrderCacheVersionService.bumpAdminOrdersCache();
        return response;
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
        VendorOrderResponse response = orderClient.updateVendorOrderStatus(
                vendorOrderId, status, internalAuth, userSub, userRoles, idempotencyKey);
        adminOrderCacheVersionService.bumpAdminOrdersCache();
        return response;
    }

    public List<VendorOrderStatusAuditResponse> getVendorOrderStatusHistory(UUID vendorOrderId, String internalAuth) {
        return orderClient.getVendorOrderStatusHistory(vendorOrderId, internalAuth);
    }

    public OrderExportJobResponse createOrderExport(
            CreateOrderExportRequest request,
            String internalAuth,
            String userSub,
            String userRoles
    ) {
        return orderClient.createOrderExport(request, internalAuth, userSub, userRoles);
    }

    public OrderExportJobResponse getOrderExportJob(UUID jobId, String internalAuth) {
        return orderClient.getOrderExportJob(jobId, internalAuth);
    }

    public ResponseEntity<byte[]> downloadOrderExport(UUID jobId, String internalAuth) {
        return orderClient.downloadOrderExport(jobId, internalAuth);
    }

    public BulkOperationResult bulkUpdateOrderStatus(
            List<UUID> orderIds, String status, String internalAuth,
            String userSub, String userRoles
    ) {
        int succeeded = 0;
        List<BulkOperationResult.BulkItemError> errors = new ArrayList<>();
        long deadline = System.currentTimeMillis() + 25_000;

        for (UUID orderId : orderIds) {
            if (System.currentTimeMillis() > deadline) {
                log.warn("Bulk status update aborted after deadline — processed {}/{}", succeeded + errors.size(), orderIds.size());
                int currentIndex = succeeded + errors.size();
                for (int i = currentIndex; i < orderIds.size(); i++) {
                    errors.add(new BulkOperationResult.BulkItemError(orderIds.get(i), "Aborted: processing deadline exceeded"));
                }
                break;
            }
            try {
                orderClient.updateOrderStatus(orderId, status, internalAuth, userSub, userRoles);
                succeeded++;
            } catch (Exception e) {
                log.warn("Bulk status update failed for order={} status={}", orderId, status, e);
                errors.add(new BulkOperationResult.BulkItemError(orderId, e.getMessage()));
            }
        }

        if (succeeded > 0) {
            adminOrderCacheVersionService.bumpAdminOrdersCache();
        }

        return new BulkOperationResult(orderIds.size(), succeeded, errors.size(), errors);
    }
}
