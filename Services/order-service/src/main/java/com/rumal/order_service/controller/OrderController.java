package com.rumal.order_service.controller;

import com.rumal.order_service.dto.CancelOrderRequest;
import com.rumal.order_service.dto.CreateOrderRequest;
import com.rumal.order_service.dto.CreateMyOrderRequest;
import com.rumal.order_service.dto.InvoiceResponse;
import com.rumal.order_service.dto.OrderDetailsResponse;
import com.rumal.order_service.dto.OrderResponse;
import com.rumal.order_service.dto.OrderStatusAuditResponse;
import com.rumal.order_service.dto.SetPaymentInfoRequest;
import com.rumal.order_service.dto.SetTrackingInfoRequest;
import com.rumal.order_service.dto.UpdateOrderNoteRequest;
import com.rumal.order_service.dto.UpdateOrderStatusRequest;
import com.rumal.order_service.dto.UpdateShippingAddressRequest;
import com.rumal.order_service.dto.VendorOrderResponse;
import com.rumal.order_service.dto.VendorOrderStatusAuditResponse;
import com.rumal.order_service.entity.OrderStatus;
import com.rumal.order_service.exception.UnauthorizedException;
import com.rumal.order_service.security.InternalRequestVerifier;
import com.rumal.order_service.service.OrderService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final InternalRequestVerifier internalRequestVerifier;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @Valid @RequestBody CreateOrderRequest req
    ) {
        internalRequestVerifier.verify(internalAuth);
        return orderService.create(req);
    }

    @GetMapping("/{id}")
    public OrderResponse get(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        return orderService.get(id);
    }
    @GetMapping
    public Page<OrderResponse> list(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) String customerEmail,
            @RequestParam(required = false) UUID vendorId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) Instant createdAfter,
            @RequestParam(required = false) Instant createdBefore,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        internalRequestVerifier.verify(internalAuth);
        return orderService.list(customerId, customerEmail, vendorId, status, createdAfter, createdBefore, pageable);
    }

    @GetMapping("/me")
    public Page<OrderResponse> listMine(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String userEmailVerified,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) Instant createdAfter,
            @RequestParam(required = false) Instant createdBefore,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        String validatedSub = requireUserSub(userSub);
        return orderService.listForKeycloakId(validatedSub, status, createdAfter, createdBefore, pageable);
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
        String validatedSub = requireUserSub(userSub);
        return orderService.createForKeycloak(validatedSub, req);
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
        String validatedSub = requireUserSub(userSub);
        return orderService.getMyDetails(validatedSub, id);
    }

    @GetMapping("/{id}/details")
    public OrderDetailsResponse details(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
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
        return orderService.updateStatus(id, req.status(), req.reason(), req.refundReason(), req.refundAmount(), req.refundedQuantity(), userSub, userRoles);
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
        return orderService.updateVendorOrderStatus(vendorOrderId, req.status(), req.reason(), req.refundReason(), req.refundAmount(), req.refundedQuantity(), userSub, userRoles);
    }

    @PatchMapping("/{id}/payment")
    public OrderResponse setPaymentInfo(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID id,
            @Valid @RequestBody SetPaymentInfoRequest req
    ) {
        internalRequestVerifier.verify(internalAuth);
        return orderService.setPaymentInfo(id, req);
    }

    @PostMapping("/me/{id}/cancel")
    public OrderResponse cancelMyOrder(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String userEmailVerified,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) CancelOrderRequest req
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        String validatedSub = requireUserSub(userSub);
        return orderService.cancelMyOrder(validatedSub, id, req);
    }

    @PatchMapping("/vendor-orders/{vendorOrderId}/tracking")
    public VendorOrderResponse setTrackingInfo(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID vendorOrderId,
            @Valid @RequestBody SetTrackingInfoRequest req
    ) {
        internalRequestVerifier.verify(internalAuth);
        return orderService.setTrackingInfo(vendorOrderId, req, null);
    }

    @PatchMapping("/{id}/note")
    public OrderResponse updateNote(
            @RequestHeader("X-Internal-Auth") String internalAuth,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOrderNoteRequest req
    ) {
        internalRequestVerifier.verify(internalAuth);
        return orderService.updateAdminNote(id, req);
    }

    @GetMapping("/me/{id}/status-history")
    public List<OrderStatusAuditResponse> myStatusHistory(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String userEmailVerified,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        String validatedSub = requireUserSub(userSub);
        return orderService.getMyStatusHistory(validatedSub, id);
    }

    @GetMapping("/me/{id}/vendor-orders")
    public List<VendorOrderResponse> myVendorOrders(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String userEmailVerified,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(userEmailVerified);
        String validatedSub = requireUserSub(userSub);
        return orderService.getMyVendorOrders(validatedSub, id);
    }

    @GetMapping("/{id}/invoice")
    public InvoiceResponse getInvoice(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        return orderService.getInvoice(id);
    }

    @GetMapping("/export")
    public void exportOrders(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) Instant createdAfter,
            @RequestParam(required = false) Instant createdBefore,
            HttpServletResponse response
    ) {
        internalRequestVerifier.verify(internalAuth);
        requireAdminRole(userRoles);
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=orders-export.csv");
        try {
            orderService.exportOrdersCsv(status, createdAfter, createdBefore, response.getWriter());
        } catch (java.io.IOException e) {
            throw new com.rumal.order_service.exception.CsvExportException("Failed to write CSV export", e);
        }
    }

    @PatchMapping("/{id}/shipping-address")
    public OrderResponse updateShippingAddress(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateShippingAddressRequest req
    ) {
        internalRequestVerifier.verify(internalAuth);
        return orderService.updateShippingAddress(id, req);
    }

    private String requireUserSub(String userSub) {
        if (userSub == null || userSub.isBlank()) {
            throw new UnauthorizedException("Missing authentication header");
        }
        return userSub.trim();
    }

    private void verifyEmailVerified(String emailVerified) {
        if (emailVerified == null || !"true".equalsIgnoreCase(emailVerified.trim())) {
            throw new UnauthorizedException("Email is not verified");
        }
    }

    private void requireAdminRole(String userRoles) {
        if (userRoles == null || userRoles.isBlank()) {
            throw new UnauthorizedException("Missing required admin role for CSV export");
        }
        boolean hasAdminRole = java.util.Arrays.stream(userRoles.split(","))
                .map(String::trim)
                .anyMatch(role -> "super_admin".equals(role) || "platform_staff".equals(role));
        if (!hasAdminRole) {
            throw new UnauthorizedException("Missing required admin role for CSV export");
        }
    }

}
