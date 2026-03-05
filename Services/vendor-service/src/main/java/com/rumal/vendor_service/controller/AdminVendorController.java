package com.rumal.vendor_service.controller;

import com.rumal.vendor_service.dto.AdminVerificationActionRequest;
import com.rumal.vendor_service.dto.UpdateVendorMetricsRequest;
import com.rumal.vendor_service.dto.UpsertVendorRequest;
import com.rumal.vendor_service.dto.UpsertVendorUserRequest;
import com.rumal.vendor_service.dto.VendorDeletionEligibilityResponse;
import com.rumal.vendor_service.dto.VendorLifecycleActionRequest;
import com.rumal.vendor_service.dto.VendorLifecycleAuditResponse;
import com.rumal.vendor_service.dto.VendorResponse;
import com.rumal.vendor_service.dto.VendorUserResponse;
import com.rumal.vendor_service.exception.UnauthorizedException;
import com.rumal.vendor_service.security.InternalRequestVerifier;
import com.rumal.vendor_service.service.VendorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/admin/vendors")
@RequiredArgsConstructor
public class AdminVendorController {

    private static final String INTERNAL_HEADER = "X-Internal-Auth";

    private final VendorService vendorService;
    private final InternalRequestVerifier internalRequestVerifier;

    @GetMapping
    public Page<VendorResponse> listAll(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "name") Pageable pageable
    ) {
        internalRequestVerifier.verify(internalAuth);
        requirePlatformVendorAdmin(userRoles);
        return vendorService.listAllNonDeleted(q, pageable);
    }

    @GetMapping("/deleted")
    public Page<VendorResponse> listDeleted(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PageableDefault(size = 20, sort = "name") Pageable pageable
    ) {
        internalRequestVerifier.verify(internalAuth);
        requirePlatformVendorAdmin(userRoles);
        return vendorService.listDeleted(pageable);
    }

    @GetMapping("/{id}/lifecycle-audit")
    public Page<VendorLifecycleAuditResponse> listLifecycleAudit(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID id,
            @PageableDefault(size = 50, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable
    ) {
        internalRequestVerifier.verify(internalAuth);
        requirePlatformVendorAdmin(userRoles);
        return vendorService.listLifecycleAudit(id, pageable);
    }

    @GetMapping("/{id}/deletion-eligibility")
    public VendorDeletionEligibilityResponse getDeletionEligibility(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        requirePlatformVendorAdmin(userRoles);
        return vendorService.getDeletionEligibility(id);
    }

    @GetMapping("/{id}")
    public VendorResponse getById(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        requirePlatformVendorAdmin(userRoles);
        return vendorService.getAdminById(id);
    }

    @PostMapping
    public VendorResponse create(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @Valid @RequestBody UpsertVendorRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        requirePlatformVendorAdmin(userRoles);
        return vendorService.create(request);
    }

    @PutMapping("/{id}")
    public VendorResponse update(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID id,
            @Valid @RequestBody UpsertVendorRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        requirePlatformVendorAdmin(userRoles);
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
        requirePlatformVendorAdmin(userRoles);
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
            @Valid @RequestBody(required = false) VendorLifecycleActionRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        requirePlatformVendorAdmin(userRoles);
        return vendorService.requestDelete(id, request == null ? null : request.reason(), userSub, userRoles);
    }

    @PostMapping("/{id}/stop-orders")
    public VendorResponse stopReceivingOrders(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @Valid @RequestBody(required = false) VendorLifecycleActionRequest request,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        requirePlatformVendorAdmin(userRoles);
        return vendorService.stopReceivingOrders(id, request == null ? null : request.reason(), userSub, userRoles);
    }

    @PostMapping("/{id}/resume-orders")
    public VendorResponse resumeReceivingOrders(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @Valid @RequestBody(required = false) VendorLifecycleActionRequest request,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        requirePlatformVendorAdmin(userRoles);
        return vendorService.resumeReceivingOrders(id, request == null ? null : request.reason(), userSub, userRoles);
    }

    @PostMapping("/{id}/restore")
    public VendorResponse restore(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @Valid @RequestBody(required = false) VendorLifecycleActionRequest request,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        requirePlatformVendorAdmin(userRoles);
        return vendorService.restore(id, request == null ? null : request.reason(), userSub, userRoles);
    }

    @PostMapping("/{id}/confirm-delete")
    public void confirmDelete(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) VendorLifecycleActionRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        requirePlatformVendorAdmin(userRoles);
        vendorService.confirmDelete(id, request == null ? null : request.reason(), userSub, userRoles);
    }

    // --- Gap 49: Verification ---
    @PostMapping("/{id}/verify")
    public VendorResponse approveVerification(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) AdminVerificationActionRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        requirePlatformVendorAdmin(userRoles);
        return vendorService.approveVerification(id, request, userSub, userRoles);
    }

    @PostMapping("/{id}/reject-verification")
    public VendorResponse rejectVerification(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) AdminVerificationActionRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        requirePlatformVendorAdmin(userRoles);
        return vendorService.rejectVerification(id, request, userSub, userRoles);
    }

    // --- Gap 50: Metrics ---
    @PatchMapping("/{id}/metrics")
    public VendorResponse updateMetrics(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateVendorMetricsRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        requirePlatformVendorAdmin(userRoles);
        return vendorService.updateMetrics(id, request);
    }

    @GetMapping("/{vendorId}/users")
    public List<VendorUserResponse> listVendorUsers(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID vendorId
    ) {
        internalRequestVerifier.verify(internalAuth);
        requirePlatformVendorAdmin(userRoles);
        return vendorService.listVendorUsers(vendorId);
    }

    @PostMapping("/{vendorId}/users")
    public VendorUserResponse addVendorUser(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID vendorId,
            @Valid @RequestBody UpsertVendorUserRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        requirePlatformVendorAdmin(userRoles);
        return vendorService.addVendorUser(vendorId, request);
    }

    @PutMapping("/{vendorId}/users/{membershipId}")
    public VendorUserResponse updateVendorUser(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID vendorId,
            @PathVariable UUID membershipId,
            @Valid @RequestBody UpsertVendorUserRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        requirePlatformVendorAdmin(userRoles);
        return vendorService.updateVendorUser(vendorId, membershipId, request);
    }

    @DeleteMapping("/{vendorId}/users/{membershipId}")
    public void removeVendorUser(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID vendorId,
            @PathVariable UUID membershipId
    ) {
        internalRequestVerifier.verify(internalAuth);
        requirePlatformVendorAdmin(userRoles);
        vendorService.removeVendorUser(vendorId, membershipId);
    }

    private void requirePlatformVendorAdmin(String userRoles) {
        Set<String> roles = parseRoles(userRoles);
        if (roles.contains("super_admin")
                || roles.contains("platform_admin")
                || roles.contains("platform_staff")) {
            return;
        }
        throw new UnauthorizedException("Caller does not have vendor admin access");
    }

    private Set<String> parseRoles(String userRoles) {
        if (userRoles == null || userRoles.isBlank()) {
            return Set.of();
        }
        Set<String> roles = new LinkedHashSet<>();
        for (String rawRole : userRoles.split(",")) {
            String normalized = normalizeRole(rawRole);
            if (!normalized.isEmpty()) {
                roles.add(normalized);
            }
        }
        return Set.copyOf(roles);
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "";
        }
        String normalized = role.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("role_")) {
            normalized = normalized.substring("role_".length());
        } else if (normalized.startsWith("role-")) {
            normalized = normalized.substring("role-".length());
        } else if (normalized.startsWith("role:")) {
            normalized = normalized.substring("role:".length());
        }
        return normalized.replace('-', '_').replace(' ', '_');
    }
}
