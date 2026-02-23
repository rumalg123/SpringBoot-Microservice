package com.rumal.admin_service.controller;

import com.rumal.admin_service.dto.VendorAdminOnboardRequest;
import com.rumal.admin_service.dto.VendorAdminOnboardResponse;
import com.rumal.admin_service.security.InternalRequestVerifier;
import com.rumal.admin_service.service.AdminVendorService;
import jakarta.validation.Valid;
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
            @RequestBody Map<String, Object> request
    ) {
        internalRequestVerifier.verify(internalAuth);
        return adminVendorService.create(request, internalAuth, userSub, userRoles);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> request
    ) {
        internalRequestVerifier.verify(internalAuth);
        return adminVendorService.update(id, request, internalAuth, userSub, userRoles);
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
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> request
    ) {
        internalRequestVerifier.verify(internalAuth);
        return adminVendorService.requestDelete(id, request, internalAuth, userSub, userRoles);
    }

    @PostMapping("/{id}/confirm-delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmDelete(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> request
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminVendorService.confirmDelete(id, request, internalAuth, userSub, userRoles);
    }

    @PostMapping("/{id}/stop-orders")
    public Map<String, Object> stopReceivingOrders(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestBody(required = false) Map<String, Object> request,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        return adminVendorService.stopReceivingOrders(id, request, internalAuth, userSub, userRoles);
    }

    @PostMapping("/{id}/resume-orders")
    public Map<String, Object> resumeReceivingOrders(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestBody(required = false) Map<String, Object> request,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        return adminVendorService.resumeReceivingOrders(id, request, internalAuth, userSub, userRoles);
    }

    @PostMapping("/{id}/restore")
    public Map<String, Object> restore(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestBody(required = false) Map<String, Object> request,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        return adminVendorService.restore(id, request, internalAuth, userSub, userRoles);
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
            @RequestBody Map<String, Object> request
    ) {
        internalRequestVerifier.verify(internalAuth);
        return adminVendorService.addVendorUser(vendorId, request, internalAuth);
    }

    @PutMapping("/{vendorId}/users/{membershipId}")
    public Map<String, Object> updateVendorUser(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID vendorId,
            @PathVariable UUID membershipId,
            @RequestBody Map<String, Object> request
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
