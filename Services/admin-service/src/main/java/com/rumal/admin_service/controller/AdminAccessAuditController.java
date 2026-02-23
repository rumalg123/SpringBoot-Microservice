package com.rumal.admin_service.controller;

import com.rumal.admin_service.exception.UnauthorizedException;
import com.rumal.admin_service.security.InternalRequestVerifier;
import com.rumal.admin_service.service.AdminAccessService;
import com.rumal.admin_service.service.AdminActorScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin/access-audit")
@RequiredArgsConstructor
public class AdminAccessAuditController {

    private final AdminAccessService adminAccessService;
    private final AdminActorScopeService adminActorScopeService;
    private final InternalRequestVerifier internalRequestVerifier;

    @GetMapping
    public Map<String, Object> list(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) UUID targetId,
            @RequestParam(required = false) UUID vendorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actorQuery,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Integer limit
    ) {
        internalRequestVerifier.verify(internalAuth);

        if (adminActorScopeService.hasRole(userRoles, "super_admin")) {
            return adminAccessService.listAccessAudit(targetType, targetId, vendorId, action, actorQuery, from, to, page, size, limit, internalAuth);
        }

        if (!adminActorScopeService.hasRole(userRoles, "vendor_admin")) {
            throw new UnauthorizedException("Caller does not have access audit read permission");
        }
        if (!"VENDOR_STAFF".equalsIgnoreCase(String.valueOf(targetType))) {
            throw new UnauthorizedException("vendor_admin can only view vendor staff access audit");
        }

        UUID effectiveVendorId = vendorId;
        if (effectiveVendorId == null && targetId != null) {
            Map<String, Object> row = adminAccessService.getVendorStaffById(targetId, internalAuth);
            Object rawVendorId = row.get("vendorId");
            if (rawVendorId != null) {
                try {
                    effectiveVendorId = UUID.fromString(String.valueOf(rawVendorId));
                } catch (IllegalArgumentException ignored) {
                    effectiveVendorId = null;
                }
            }
        }
        if (effectiveVendorId == null) {
            throw new UnauthorizedException("vendorId is required for vendor_admin access audit queries");
        }
        adminActorScopeService.assertCanManageVendorStaffVendor(userSub, userRoles, effectiveVendorId, internalAuth);
        return adminAccessService.listAccessAudit("VENDOR_STAFF", targetId, effectiveVendorId, action, actorQuery, from, to, page, size, limit, internalAuth);
    }
}
