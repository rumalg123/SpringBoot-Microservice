package com.rumal.admin_service.controller;

import com.rumal.admin_service.dto.VendorAdminOnboardRequest;
import com.rumal.admin_service.dto.VendorAdminOnboardResponse;
import com.rumal.admin_service.security.InternalRequestVerifier;
import com.rumal.admin_service.service.AdminAuditService;
import com.rumal.admin_service.service.AdminVendorService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin/vendors")
@RequiredArgsConstructor
public class AdminVendorController {

    private final AdminVendorService adminVendorService;
    private final InternalRequestVerifier internalRequestVerifier;
    private final AdminAuditService auditService;

    @GetMapping
    public List<Map<String, Object>> listAll(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth
    ) {
        internalRequestVerifier.verify(internalAuth);
        return adminVendorService.listAll(internalAuth);
    }

    @GetMapping("/deleted")
    public List<Map<String, Object>> listDeleted(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth
    ) {
        internalRequestVerifier.verify(internalAuth);
        return adminVendorService.listDeleted(internalAuth);
    }

    @GetMapping("/{id}/lifecycle-audit")
    public List<Map<String, Object>> listLifecycleAudit(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        return adminVendorService.listLifecycleAudit(id, internalAuth);
    }

    @GetMapping("/{id}")
    public Map<String, Object> getById(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        return adminVendorService.getById(id, internalAuth);
    }

    @GetMapping("/{id}/deletion-eligibility")
    public Map<String, Object> getDeletionEligibility(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        return adminVendorService.getDeletionEligibility(id, internalAuth);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestBody @NotNull Map<String, Object> request
    ) {
        internalRequestVerifier.verify(internalAuth);
        Map<String, Object> result = adminVendorService.create(request, internalAuth, userSub, userRoles);
        auditService.log(userSub, userRoles, "CREATE_VENDOR", "VENDOR", String.valueOf(result.get("id")), null, null);
        return result;
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID id,
            @RequestBody @NotNull Map<String, Object> request
    ) {
        internalRequestVerifier.verify(internalAuth);
        Map<String, Object> result = adminVendorService.update(id, request, internalAuth, userSub, userRoles);
        auditService.log(userSub, userRoles, "UPDATE_VENDOR", "VENDOR", id.toString(), null, null);
        return result;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        throw new ResponseStatusException(
                HttpStatus.METHOD_NOT_ALLOWED,
                "Legacy DELETE is disabled. Use /delete-request then /confirm-delete."
        );
    }

    @PostMapping("/{id}/delete-request")
    public Map<String, Object> requestDelete(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> request
    ) {
        internalRequestVerifier.verify(internalAuth);
        Map<String, Object> result = adminVendorService.requestDelete(id, request, internalAuth, userSub, userRoles, idempotencyKey);
        auditService.log(userSub, userRoles, "REQUEST_DELETE_VENDOR", "VENDOR", id.toString(), null, null);
        return result;
    }

    @PostMapping("/{id}/confirm-delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmDelete(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> request
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminVendorService.confirmDelete(id, request, internalAuth, userSub, userRoles, idempotencyKey);
        auditService.log(userSub, userRoles, "CONFIRM_DELETE_VENDOR", "VENDOR", id.toString(), null, null);
    }

    @PostMapping("/{id}/stop-orders")
    public Map<String, Object> stopReceivingOrders(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) Map<String, Object> request,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        Map<String, Object> result = adminVendorService.stopReceivingOrders(id, request, internalAuth, userSub, userRoles, idempotencyKey);
        auditService.log(userSub, userRoles, "STOP_VENDOR_ORDERS", "VENDOR", id.toString(), null, null);
        return result;
    }

    @PostMapping("/{id}/resume-orders")
    public Map<String, Object> resumeReceivingOrders(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) Map<String, Object> request,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        Map<String, Object> result = adminVendorService.resumeReceivingOrders(id, request, internalAuth, userSub, userRoles, idempotencyKey);
        auditService.log(userSub, userRoles, "RESUME_VENDOR_ORDERS", "VENDOR", id.toString(), null, null);
        return result;
    }

    @PostMapping("/{id}/restore")
    public Map<String, Object> restore(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) Map<String, Object> request,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        Map<String, Object> result = adminVendorService.restore(id, request, internalAuth, userSub, userRoles, idempotencyKey);
        auditService.log(userSub, userRoles, "RESTORE_VENDOR", "VENDOR", id.toString(), null, null);
        return result;
    }

    @GetMapping("/{vendorId}/users")
    public List<Map<String, Object>> listVendorUsers(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID vendorId
    ) {
        internalRequestVerifier.verify(internalAuth);
        return adminVendorService.listVendorUsers(vendorId, internalAuth);
    }

    @PostMapping("/{vendorId}/users")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> addVendorUser(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID vendorId,
            @RequestBody @NotNull Map<String, Object> request
    ) {
        internalRequestVerifier.verify(internalAuth);
        return adminVendorService.addVendorUser(vendorId, request, internalAuth);
    }

    @PutMapping("/{vendorId}/users/{membershipId}")
    public Map<String, Object> updateVendorUser(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID vendorId,
            @PathVariable UUID membershipId,
            @RequestBody @NotNull Map<String, Object> request
    ) {
        internalRequestVerifier.verify(internalAuth);
        return adminVendorService.updateVendorUser(vendorId, membershipId, request, internalAuth);
    }

    @DeleteMapping("/{vendorId}/users/{membershipId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeVendorUser(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID vendorId,
            @PathVariable UUID membershipId
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminVendorService.removeVendorUser(vendorId, membershipId, internalAuth);
    }

    @PostMapping("/{vendorId}/users/onboard")
    @ResponseStatus(HttpStatus.CREATED)
    public VendorAdminOnboardResponse onboardVendorAdmin(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID vendorId,
            @Valid @RequestBody VendorAdminOnboardRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        return adminVendorService.onboardVendorAdmin(vendorId, request, internalAuth);
    }
}
