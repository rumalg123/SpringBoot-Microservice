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

    private static final String VENDOR_ID_FIELD = "vendorId";

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
        return adminAccessService.listVendorStaff(scopedVendorId, internalAuth, userRoles, scopedVendorId);
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
        List<Map<String, Object>> rows = adminAccessService.listDeletedVendorStaff(internalAuth, userRoles, scopedVendorId);
        if (scopedVendorId == null) {
            return rows;
        }
        return rows.stream()
                .filter(row -> scopedVendorId.equals(parseUuid(row.get(VENDOR_ID_FIELD))))
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
        UUID vendorId = parseUuid(request.get(VENDOR_ID_FIELD));
        UUID scopedVendorId = adminActorScopeService.resolveScopedVendorIdForVendorStaffManagement(userSub, userRoles, vendorId, internalAuth);
        Map<String, Object> created = adminAccessService.createVendorStaff(withVendorId(request, scopedVendorId), internalAuth, userSub, userRoles, actionReason);
        adminVendorService.syncVendorStaffMembershipTransition(null, created, internalAuth, userSub, userRoles);
        return created;
    }

    @GetMapping("/{id}")
    public Map<String, Object> getById(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(required = false) UUID vendorId,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        UUID scopedVendorId = adminActorScopeService.resolveScopedVendorIdForVendorStaffManagement(userSub, userRoles, vendorId, internalAuth);
        Map<String, Object> row = adminAccessService.getVendorStaffById(id, internalAuth, userRoles, scopedVendorId);
        UUID rowVendorId = parseUuid(row.get(VENDOR_ID_FIELD));
        if (rowVendorId != null) {
            adminActorScopeService.assertCanManageVendorStaffVendor(userSub, userRoles, rowVendorId, internalAuth);
        }
        return row;
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "X-Action-Reason", required = false) String actionReason,
            @RequestParam(required = false) UUID vendorId,
            @PathVariable UUID id,
            @RequestBody @NotNull Map<String, Object> request
    ) {
        internalRequestVerifier.verify(internalAuth);
        UUID requestedVendorId = parseUuid(request.get(VENDOR_ID_FIELD));
        if (requestedVendorId == null) {
            requestedVendorId = vendorId;
        }
        UUID scopedVendorId = adminActorScopeService.resolveScopedVendorIdForVendorStaffManagement(userSub, userRoles, requestedVendorId, internalAuth);
        Map<String, Object> existing = adminAccessService.getVendorStaffById(id, internalAuth, userRoles, scopedVendorId);
        UUID existingVendorId = parseUuid(existing.get(VENDOR_ID_FIELD));
        UUID effectiveVendorId = requestedVendorId != null ? requestedVendorId : existingVendorId;
        if (effectiveVendorId != null) {
            adminActorScopeService.assertCanManageVendorStaffVendor(userSub, userRoles, effectiveVendorId, internalAuth);
        }
        UUID updateVendorId = scopedVendorId != null ? scopedVendorId : effectiveVendorId;
        Map<String, Object> updated = adminAccessService.updateVendorStaff(id, withVendorId(request, updateVendorId), internalAuth, userSub, userRoles, actionReason);
        adminVendorService.syncVendorStaffMembershipTransition(existing, updated, internalAuth, userSub, userRoles);
        return updated;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "X-Action-Reason", required = false) String actionReason,
            @RequestParam(required = false) UUID vendorId,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        UUID scopedVendorId = adminActorScopeService.resolveScopedVendorIdForVendorStaffManagement(userSub, userRoles, vendorId, internalAuth);
        Map<String, Object> existing = adminAccessService.getVendorStaffById(id, internalAuth, userRoles, scopedVendorId);
        UUID existingVendorId = parseUuid(existing.get(VENDOR_ID_FIELD));
        if (existingVendorId != null) {
            adminActorScopeService.assertCanManageVendorStaffVendor(userSub, userRoles, existingVendorId, internalAuth);
        }
        adminAccessService.deleteVendorStaff(id, internalAuth, userSub, userRoles, actionReason, scopedVendorId);
        adminVendorService.syncVendorStaffMembershipTransition(existing, null, internalAuth, userSub, userRoles);
    }

    @PostMapping("/{id}/restore")
    public Map<String, Object> restore(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "X-Action-Reason", required = false) String actionReason,
            @RequestParam(required = false) UUID vendorId,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        UUID scopedVendorId = adminActorScopeService.resolveScopedVendorIdForVendorStaffManagement(userSub, userRoles, vendorId, internalAuth);
        Map<String, Object> existing = adminAccessService.getVendorStaffById(id, internalAuth, userRoles, scopedVendorId);
        UUID existingVendorId = parseUuid(existing.get(VENDOR_ID_FIELD));
        if (existingVendorId != null) {
            adminActorScopeService.assertCanManageVendorStaffVendor(userSub, userRoles, existingVendorId, internalAuth);
        }
        Map<String, Object> restored = adminAccessService.restoreVendorStaff(id, internalAuth, userSub, userRoles, actionReason, scopedVendorId);
        adminVendorService.syncVendorStaffMembershipTransition(existing, restored, internalAuth, userSub, userRoles);
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
        int synced = adminVendorService.reconcileVendorStaffMemberships(scopedVendorId, internalAuth, userSub, userRoles);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put(VENDOR_ID_FIELD, scopedVendorId);
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
        copy.put(VENDOR_ID_FIELD, vendorId);
        return copy;
    }
}
