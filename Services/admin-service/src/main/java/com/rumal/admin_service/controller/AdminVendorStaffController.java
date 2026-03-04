package com.rumal.admin_service.controller;

import com.rumal.admin_service.security.InternalRequestVerifier;
import com.rumal.admin_service.service.AdminActorScopeService;
import com.rumal.admin_service.service.AdminAccessService;
import com.rumal.admin_service.service.AdminVendorService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;

@RestController
@RequestMapping("/admin/vendor-staff")
@RequiredArgsConstructor
public class AdminVendorStaffController {

    private final AdminAccessService adminAccessService;
    private final AdminVendorService adminVendorService;
    private final AdminActorScopeService adminActorScopeService;
    private final InternalRequestVerifier internalRequestVerifier;

    @GetMapping
    public List<Map<String, Object>> listAll(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(required = false) UUID vendorId
    ) {
        internalRequestVerifier.verify(internalAuth);
        UUID scopedVendorId = adminActorScopeService.resolveScopedVendorIdForVendorStaffManagement(userSub, userRoles, vendorId, internalAuth);
        return adminAccessService.listVendorStaff(scopedVendorId, internalAuth);
    }

    @GetMapping("/deleted")
    public List<Map<String, Object>> listDeleted(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(required = false) UUID vendorId
    ) {
        internalRequestVerifier.verify(internalAuth);
        UUID scopedVendorId = adminActorScopeService.resolveScopedVendorIdForVendorStaffManagement(userSub, userRoles, vendorId, internalAuth);
        List<Map<String, Object>> rows = adminAccessService.listDeletedVendorStaff(internalAuth);
        if (scopedVendorId == null) {
            return rows;
        }
        return rows.stream()
                .filter(row -> scopedVendorId.equals(parseUuid(row.get("vendorId"))))
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "X-Action-Reason", required = false) String actionReason,
            @RequestBody @NotNull Map<String, Object> request
    ) {
        internalRequestVerifier.verify(internalAuth);
        UUID vendorId = parseUuid(request.get("vendorId"));
        UUID scopedVendorId = adminActorScopeService.resolveScopedVendorIdForVendorStaffManagement(userSub, userRoles, vendorId, internalAuth);
        Map<String, Object> created = adminAccessService.createVendorStaff(withVendorId(request, scopedVendorId), internalAuth, userSub, userRoles, actionReason);
        adminVendorService.syncVendorStaffMembershipTransition(null, created, internalAuth);
        return created;
    }

    @GetMapping("/{id}")
    public Map<String, Object> getById(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        Map<String, Object> row = adminAccessService.getVendorStaffById(id, internalAuth);
        UUID vendorId = parseUuid(row.get("vendorId"));
        if (vendorId != null) {
            adminActorScopeService.assertCanManageVendorStaffVendor(userSub, userRoles, vendorId, internalAuth);
        }
        return row;
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "X-Action-Reason", required = false) String actionReason,
            @PathVariable UUID id,
            @RequestBody @NotNull Map<String, Object> request
    ) {
        internalRequestVerifier.verify(internalAuth);
        Map<String, Object> existing = adminAccessService.getVendorStaffById(id, internalAuth);
        UUID vendorId = parseUuid(request.get("vendorId"));
        if (vendorId == null) {
            vendorId = parseUuid(existing.get("vendorId"));
        }
        UUID scopedVendorId = adminActorScopeService.resolveScopedVendorIdForVendorStaffManagement(userSub, userRoles, vendorId, internalAuth);
        Map<String, Object> updated = adminAccessService.updateVendorStaff(id, withVendorId(request, scopedVendorId), internalAuth, userSub, userRoles, actionReason);
        adminVendorService.syncVendorStaffMembershipTransition(existing, updated, internalAuth);
        return updated;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "X-Action-Reason", required = false) String actionReason,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        Map<String, Object> existing = adminAccessService.getVendorStaffById(id, internalAuth);
        UUID vendorId = parseUuid(existing.get("vendorId"));
        if (vendorId != null) {
            adminActorScopeService.assertCanManageVendorStaffVendor(userSub, userRoles, vendorId, internalAuth);
        }
        adminAccessService.deleteVendorStaff(id, internalAuth, userSub, userRoles, actionReason);
        adminVendorService.syncVendorStaffMembershipTransition(existing, null, internalAuth);
    }

    @PostMapping("/{id}/restore")
    public Map<String, Object> restore(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "X-Action-Reason", required = false) String actionReason,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        Map<String, Object> existing = adminAccessService.getVendorStaffById(id, internalAuth);
        UUID vendorId = parseUuid(existing.get("vendorId"));
        if (vendorId != null) {
            adminActorScopeService.assertCanManageVendorStaffVendor(userSub, userRoles, vendorId, internalAuth);
        }
        Map<String, Object> restored = adminAccessService.restoreVendorStaff(id, internalAuth, userSub, userRoles, actionReason);
        adminVendorService.syncVendorStaffMembershipTransition(existing, restored, internalAuth);
        return restored;
    }

    @PostMapping("/sync-memberships")
    public Map<String, Object> syncMemberships(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(required = false) UUID vendorId
    ) {
        internalRequestVerifier.verify(internalAuth);
        UUID scopedVendorId = adminActorScopeService.resolveScopedVendorIdForVendorStaffManagement(userSub, userRoles, vendorId, internalAuth);
        int synced = adminVendorService.reconcileVendorStaffMemberships(scopedVendorId, internalAuth);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("vendorId", scopedVendorId);
        response.put("syncedCount", synced);
        return response;
    }

    private UUID parseUuid(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(raw));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Map<String, Object> withVendorId(Map<String, Object> request, UUID vendorId) {
        Map<String, Object> copy = new LinkedHashMap<>(request == null ? Map.of() : request);
        copy.put("vendorId", vendorId);
        return copy;
    }
}
