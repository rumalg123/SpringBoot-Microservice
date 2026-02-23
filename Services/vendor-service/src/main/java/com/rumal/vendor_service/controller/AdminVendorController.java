package com.rumal.vendor_service.controller;

import com.rumal.vendor_service.dto.UpsertVendorRequest;
import com.rumal.vendor_service.dto.UpsertVendorUserRequest;
import com.rumal.vendor_service.dto.VendorDeletionEligibilityResponse;
import com.rumal.vendor_service.dto.VendorLifecycleActionRequest;
import com.rumal.vendor_service.dto.VendorLifecycleAuditResponse;
import com.rumal.vendor_service.dto.VendorResponse;
import com.rumal.vendor_service.dto.VendorUserResponse;
import com.rumal.vendor_service.security.InternalRequestVerifier;
import com.rumal.vendor_service.service.VendorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/vendors")
@RequiredArgsConstructor
public class AdminVendorController {

    private static final String INTERNAL_HEADER = "X-Internal-Auth";

    private final VendorService vendorService;
    private final InternalRequestVerifier internalRequestVerifier;

    @GetMapping
    public List<VendorResponse> listAll(@RequestHeader(INTERNAL_HEADER) String internalAuth) {
        internalRequestVerifier.verify(internalAuth);
        return vendorService.listAllNonDeleted();
    }

    @GetMapping("/deleted")
    public List<VendorResponse> listDeleted(@RequestHeader(INTERNAL_HEADER) String internalAuth) {
        internalRequestVerifier.verify(internalAuth);
        return vendorService.listDeleted();
    }

    @GetMapping("/{id}/lifecycle-audit")
    public List<VendorLifecycleAuditResponse> listLifecycleAudit(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        return vendorService.listLifecycleAudit(id);
    }

    @GetMapping("/{id}/deletion-eligibility")
    public VendorDeletionEligibilityResponse getDeletionEligibility(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        return vendorService.getDeletionEligibility(id);
    }

    @GetMapping("/{id}")
    public VendorResponse getById(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        return vendorService.getAdminById(id);
    }

    @PostMapping
    public VendorResponse create(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @Valid @RequestBody UpsertVendorRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        return vendorService.create(request);
    }

    @PutMapping("/{id}")
    public VendorResponse update(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @PathVariable UUID id,
            @Valid @RequestBody UpsertVendorRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        return vendorService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
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
    public VendorResponse requestDelete(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID id,
            @RequestBody(required = false) VendorLifecycleActionRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        return vendorService.requestDelete(id, request == null ? null : request.reason(), userSub, userRoles);
    }

    @PostMapping("/{id}/stop-orders")
    public VendorResponse stopReceivingOrders(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestBody(required = false) VendorLifecycleActionRequest request,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        return vendorService.stopReceivingOrders(id, request == null ? null : request.reason(), userSub, userRoles);
    }

    @PostMapping("/{id}/resume-orders")
    public VendorResponse resumeReceivingOrders(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestBody(required = false) VendorLifecycleActionRequest request,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        return vendorService.resumeReceivingOrders(id, request == null ? null : request.reason(), userSub, userRoles);
    }

    @PostMapping("/{id}/restore")
    public VendorResponse restore(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestBody(required = false) VendorLifecycleActionRequest request,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        return vendorService.restore(id, request == null ? null : request.reason(), userSub, userRoles);
    }

    @PostMapping("/{id}/confirm-delete")
    public void confirmDelete(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID id,
            @RequestBody(required = false) VendorLifecycleActionRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        vendorService.confirmDelete(id, request == null ? null : request.reason(), userSub, userRoles);
    }

    @GetMapping("/{vendorId}/users")
    public List<VendorUserResponse> listVendorUsers(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @PathVariable UUID vendorId
    ) {
        internalRequestVerifier.verify(internalAuth);
        return vendorService.listVendorUsers(vendorId);
    }

    @PostMapping("/{vendorId}/users")
    public VendorUserResponse addVendorUser(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @PathVariable UUID vendorId,
            @Valid @RequestBody UpsertVendorUserRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        return vendorService.addVendorUser(vendorId, request);
    }

    @PutMapping("/{vendorId}/users/{membershipId}")
    public VendorUserResponse updateVendorUser(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @PathVariable UUID vendorId,
            @PathVariable UUID membershipId,
            @Valid @RequestBody UpsertVendorUserRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        return vendorService.updateVendorUser(vendorId, membershipId, request);
    }

    @DeleteMapping("/{vendorId}/users/{membershipId}")
    public void removeVendorUser(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @PathVariable UUID vendorId,
            @PathVariable UUID membershipId
    ) {
        internalRequestVerifier.verify(internalAuth);
        vendorService.removeVendorUser(vendorId, membershipId);
    }
}
