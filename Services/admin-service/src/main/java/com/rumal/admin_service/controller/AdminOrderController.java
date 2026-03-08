package com.rumal.admin_service.controller;

import com.rumal.admin_service.dto.BulkOperationResult;
import com.rumal.admin_service.dto.BulkUpdateOrderStatusRequest;
import com.rumal.admin_service.dto.CreateOrderExportRequest;
import com.rumal.admin_service.dto.OrderResponse;
import com.rumal.admin_service.dto.OrderExportJobResponse;
import com.rumal.admin_service.dto.OrderStatusAuditResponse;
import com.rumal.admin_service.dto.PageResponse;
import com.rumal.admin_service.dto.UpdateOrderNoteRequest;
import com.rumal.admin_service.dto.UpdateOrderStatusRequest;
import com.rumal.admin_service.dto.VendorOrderResponse;
import com.rumal.admin_service.dto.VendorOrderStatusAuditResponse;
import com.rumal.admin_service.security.InternalRequestVerifier;
import com.rumal.admin_service.service.AdminActorScopeService;
import com.rumal.admin_service.service.AdminAuditService;
import com.rumal.admin_service.service.AdminOrderService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final AdminOrderService adminOrderService;
    private final AdminActorScopeService adminActorScopeService;
    private final InternalRequestVerifier internalRequestVerifier;
    private final AdminAuditService auditService;

    @GetMapping
    public PageResponse<OrderResponse> listOrders(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) String customerEmail,
            @RequestParam(required = false) UUID vendorId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Instant createdAfter,
            @RequestParam(required = false) Instant createdBefore,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,DESC") List<String> sort
    ) {
        internalRequestVerifier.verify(internalAuth);
        UUID scopedVendorId = adminActorScopeService.resolveScopedVendorIdForOrderAccess(userSub, userRoles, vendorId, internalAuth);
        return adminOrderService.listOrders(customerId, customerEmail, scopedVendorId, status, createdAfter, createdBefore, page, size, sort, internalAuth);
    }

    @PatchMapping("/{orderId}/status")
    public OrderResponse updateOrderStatus(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable UUID orderId,
            @Valid @org.springframework.web.bind.annotation.RequestBody UpdateOrderStatusRequest request,
            HttpServletRequest httpRequest
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminActorScopeService.assertCanUpdateOrderStatus(userSub, userRoles, orderId, internalAuth);
        OrderResponse response = adminOrderService.updateOrderStatus(orderId, request.status(), internalAuth, userSub, userRoles, idempotencyKey);
        auditService.log(userSub, userRoles, "UPDATE_ORDER_STATUS", "ORDER", orderId.toString(), "status=" + request.status(), extractClientIp(httpRequest));
        return response;
    }

    @PatchMapping("/{orderId}/note")
    public OrderResponse updateOrderNote(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable UUID orderId,
            @Valid @org.springframework.web.bind.annotation.RequestBody UpdateOrderNoteRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminActorScopeService.assertCanUpdateOrderStatus(userSub, userRoles, orderId, internalAuth);
        return adminOrderService.updateOrderNote(orderId, request, internalAuth, idempotencyKey);
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
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable UUID vendorOrderId,
            @Valid @org.springframework.web.bind.annotation.RequestBody UpdateOrderStatusRequest request,
            HttpServletRequest httpRequest
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminActorScopeService.assertCanUpdateVendorOrderStatus(userSub, userRoles, vendorOrderId, internalAuth);
        VendorOrderResponse response = adminOrderService.updateVendorOrderStatus(vendorOrderId, request.status(), internalAuth, userSub, userRoles, idempotencyKey);
        auditService.log(userSub, userRoles, "UPDATE_VENDOR_ORDER_STATUS", "VENDOR_ORDER", vendorOrderId.toString(), "status=" + request.status(), extractClientIp(httpRequest));
        return response;
    }

    @PostMapping("/bulk-status-update")
    public BulkOperationResult bulkUpdateOrderStatus(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @Valid @RequestBody BulkUpdateOrderStatusRequest request,
            HttpServletRequest httpRequest
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminActorScopeService.assertCanManageOrders(userSub, userRoles, internalAuth);
        BulkOperationResult result = adminOrderService.bulkUpdateOrderStatus(
                request.orderIds(), request.status(), internalAuth, userSub, userRoles);
        auditService.log(userSub, userRoles, "BULK_UPDATE_ORDER_STATUS", "ORDER",
                request.orderIds().size() + " orders", "status=" + request.status() + " succeeded=" + result.succeeded() + " failed=" + result.failed(), extractClientIp(httpRequest));
        return result;
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

    @GetMapping("/export")
    public ResponseEntity<OrderExportJobResponse> exportOrders(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String customerEmail,
            @RequestParam(required = false) String createdAfter,
            @RequestParam(required = false) String createdBefore
    ) {
        internalRequestVerifier.verify(internalAuth);
        UUID scopedVendorId = adminActorScopeService.resolveScopedVendorIdForOrderAccess(userSub, userRoles, null, internalAuth);
        OrderExportJobResponse exportJob = adminOrderService.createOrderExport(
                new CreateOrderExportRequest(
                        format,
                        status,
                        customerEmail,
                        parseInstant(createdAfter),
                        parseInstant(createdBefore),
                        scopedVendorId
                ),
                internalAuth,
                userSub,
                userRoles
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(exportJob);
    }

    @PostMapping("/exports")
    public ResponseEntity<OrderExportJobResponse> createOrderExport(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestBody(required = false) CreateOrderExportRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        CreateOrderExportRequest safeRequest = request == null
                ? new CreateOrderExportRequest("csv", null, null, null, null, null)
                : request;
        UUID scopedVendorId = adminActorScopeService.resolveScopedVendorIdForOrderAccess(
                userSub,
                userRoles,
                safeRequest.vendorId(),
                internalAuth
        );
        OrderExportJobResponse exportJob = adminOrderService.createOrderExport(
                new CreateOrderExportRequest(
                        safeRequest.format(),
                        safeRequest.status(),
                        safeRequest.customerEmail(),
                        safeRequest.createdAfter(),
                        safeRequest.createdBefore(),
                        scopedVendorId
                ),
                internalAuth,
                userSub,
                userRoles
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(exportJob);
    }

    @GetMapping("/exports/{jobId}")
    public OrderExportJobResponse getOrderExportJob(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID jobId
    ) {
        internalRequestVerifier.verify(internalAuth);
        OrderExportJobResponse exportJob = adminOrderService.getOrderExportJob(jobId, internalAuth);
        adminActorScopeService.assertCanAccessOrderExport(userSub, userRoles, exportJob.vendorId(), internalAuth);
        return exportJob;
    }

    @GetMapping("/exports/{jobId}/download")
    public ResponseEntity<byte[]> downloadOrderExport(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID jobId
    ) {
        internalRequestVerifier.verify(internalAuth);
        OrderExportJobResponse exportJob = adminOrderService.getOrderExportJob(jobId, internalAuth);
        adminActorScopeService.assertCanAccessOrderExport(userSub, userRoles, exportJob.vendorId(), internalAuth);
        ResponseEntity<byte[]> response = adminOrderService.downloadOrderExport(jobId, internalAuth);
        return ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
                .body(response.getBody());
    }

    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private Instant parseInstant(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        return Instant.parse(rawValue.trim());
    }
}
