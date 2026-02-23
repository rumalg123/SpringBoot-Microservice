package com.rumal.admin_service.controller;

import com.rumal.admin_service.dto.OrderResponse;
import com.rumal.admin_service.dto.OrderStatusAuditResponse;
import com.rumal.admin_service.dto.PageResponse;
import com.rumal.admin_service.dto.UpdateOrderStatusRequest;
import com.rumal.admin_service.dto.VendorOrderResponse;
import com.rumal.admin_service.dto.VendorOrderStatusAuditResponse;
import com.rumal.admin_service.security.InternalRequestVerifier;
import com.rumal.admin_service.service.AdminActorScopeService;
import com.rumal.admin_service.service.AdminOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final AdminOrderService adminOrderService;
    private final AdminActorScopeService adminActorScopeService;
    private final InternalRequestVerifier internalRequestVerifier;

    @GetMapping
    public PageResponse<OrderResponse> listOrders(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) String customerEmail,
            @RequestParam(required = false) UUID vendorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,DESC") List<String> sort
    ) {
        internalRequestVerifier.verify(internalAuth);
        UUID scopedVendorId = adminActorScopeService.resolveScopedVendorIdForOrderAccess(userSub, userRoles, vendorId, internalAuth);
        return adminOrderService.listOrders(customerId, customerEmail, scopedVendorId, page, size, sort, internalAuth);
    }

    @PatchMapping("/{orderId}/status")
    public OrderResponse updateOrderStatus(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID orderId,
            @Valid @org.springframework.web.bind.annotation.RequestBody UpdateOrderStatusRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminActorScopeService.assertCanUpdateOrderStatus(userSub, userRoles, orderId, internalAuth);
        return adminOrderService.updateOrderStatus(orderId, request.status(), internalAuth, userSub, userRoles);
    }

    @GetMapping("/{orderId}/status-history")
    public List<OrderStatusAuditResponse> getOrderStatusHistory(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID orderId
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminActorScopeService.assertCanReadOrderHistory(userSub, userRoles, orderId, internalAuth);
        return adminOrderService.getOrderStatusHistory(orderId, internalAuth);
    }

    @GetMapping("/{orderId}/vendor-orders")
    public List<VendorOrderResponse> getVendorOrders(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID orderId
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminActorScopeService.assertCanReadOrderHistory(userSub, userRoles, orderId, internalAuth);
        return adminOrderService.getVendorOrders(orderId, internalAuth);
    }

    @PatchMapping("/vendor-orders/{vendorOrderId}/status")
    public VendorOrderResponse updateVendorOrderStatus(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID vendorOrderId,
            @Valid @org.springframework.web.bind.annotation.RequestBody UpdateOrderStatusRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminActorScopeService.assertCanUpdateVendorOrderStatus(userSub, userRoles, vendorOrderId, internalAuth);
        return adminOrderService.updateVendorOrderStatus(vendorOrderId, request.status(), internalAuth, userSub, userRoles);
    }

    @GetMapping("/vendor-orders/{vendorOrderId}/status-history")
    public List<VendorOrderStatusAuditResponse> getVendorOrderStatusHistory(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID vendorOrderId
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminActorScopeService.assertCanReadVendorOrderHistory(userSub, userRoles, vendorOrderId, internalAuth);
        return adminOrderService.getVendorOrderStatusHistory(vendorOrderId, internalAuth);
    }
}
