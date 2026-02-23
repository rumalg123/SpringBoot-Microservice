package com.rumal.order_service.controller;

import com.rumal.order_service.dto.CreateOrderRequest;
import com.rumal.order_service.dto.CreateMyOrderRequest;
import com.rumal.order_service.dto.OrderDetailsResponse;
import com.rumal.order_service.dto.OrderResponse;
import com.rumal.order_service.dto.OrderStatusAuditResponse;
import com.rumal.order_service.dto.UpdateOrderStatusRequest;
import com.rumal.order_service.dto.VendorOrderResponse;
import com.rumal.order_service.dto.VendorOrderStatusAuditResponse;
import com.rumal.order_service.exception.UnauthorizedException;
import com.rumal.order_service.security.InternalRequestVerifier;
import com.rumal.order_service.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final InternalRequestVerifier internalRequestVerifier;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@Valid @RequestBody CreateOrderRequest req) {
        return orderService.create(req);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable UUID id) {
        return orderService.get(id);
    }
    @GetMapping
    public Page<OrderResponse> list(
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) String customerEmail,
            @RequestParam(required = false) UUID vendorId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return orderService.list(customerId, customerEmail, vendorId, pageable);
    }

    @GetMapping("/me")
    public Page<OrderResponse> listMine(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String userEmailVerified,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        if (userSub == null || userSub.isBlank()) {
            throw new UnauthorizedException("Missing authentication header");
        }
        return orderService.listForKeycloakId(userSub, pageable);
    }

    @PostMapping("/me")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createMine(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String userEmailVerified,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @Valid @RequestBody CreateMyOrderRequest req
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        if (userSub == null || userSub.isBlank()) {
            throw new UnauthorizedException("Missing authentication header");
        }
        return orderService.createForKeycloak(userSub, req);
    }

    @GetMapping("/me/{id}")
    public OrderDetailsResponse detailsMine(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String userEmailVerified,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        if (userSub == null || userSub.isBlank()) {
            throw new UnauthorizedException("Missing authentication header");
        }
        return orderService.getMyDetails(userSub, id);
    }

    @GetMapping("/{id}/details")
    public OrderDetailsResponse details(@PathVariable UUID id) {
        return orderService.getDetails(id);
    }

    @GetMapping("/{id}/status-history")
    public java.util.List<OrderStatusAuditResponse> statusHistory(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        return orderService.getStatusHistory(id);
    }

    @GetMapping("/{id}/vendor-orders")
    public java.util.List<VendorOrderResponse> vendorOrders(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        return orderService.getVendorOrders(id);
    }

    @GetMapping("/vendor-orders/{vendorOrderId}")
    public VendorOrderResponse getVendorOrder(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID vendorOrderId
    ) {
        internalRequestVerifier.verify(internalAuth);
        return orderService.getVendorOrder(vendorOrderId);
    }

    @GetMapping("/vendor-orders/{vendorOrderId}/status-history")
    public java.util.List<VendorOrderStatusAuditResponse> vendorOrderStatusHistory(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID vendorOrderId
    ) {
        internalRequestVerifier.verify(internalAuth);
        return orderService.getVendorOrderStatusHistory(vendorOrderId);
    }

    @PatchMapping("/{id}/status")
    public OrderResponse updateStatus(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOrderStatusRequest req
    ) {
        internalRequestVerifier.verify(internalAuth);
        return orderService.updateStatus(id, req.status(), userSub, userRoles);
    }

    @PatchMapping("/vendor-orders/{vendorOrderId}/status")
    public VendorOrderResponse updateVendorOrderStatus(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID vendorOrderId,
            @Valid @RequestBody UpdateOrderStatusRequest req
    ) {
        internalRequestVerifier.verify(internalAuth);
        return orderService.updateVendorOrderStatus(vendorOrderId, req.status(), userSub, userRoles);
    }

    private void verifyEmailVerified(String emailVerified) {
        if (emailVerified == null || !"true".equalsIgnoreCase(emailVerified.trim())) {
            throw new UnauthorizedException("Email is not verified");
        }
    }

}
