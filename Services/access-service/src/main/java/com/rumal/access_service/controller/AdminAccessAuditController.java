package com.rumal.access_service.controller;

import com.rumal.access_service.dto.AccessChangeAuditPageResponse;
import com.rumal.access_service.exception.UnauthorizedException;
import com.rumal.access_service.security.InternalRequestVerifier;
import com.rumal.access_service.service.AccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/admin/access-audit")
@RequiredArgsConstructor
public class AdminAccessAuditController {

    private static final String INTERNAL_HEADER = "X-Internal-Auth";

    private final InternalRequestVerifier internalRequestVerifier;
    private final AccessService accessService;

    @GetMapping
    public AccessChangeAuditPageResponse list(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "X-Caller-Vendor-Id", required = false) UUID callerVendorId,
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
        UUID effectiveVendorId = resolveEffectiveVendorId(userRoles, callerVendorId, vendorId);
        return accessService.listAccessAudit(targetType, targetId, effectiveVendorId, action, actorQuery, from, to, page, size, limit);
    }

    private UUID resolveEffectiveVendorId(String userRoles, UUID callerVendorId, UUID requestedVendorId) {
        Set<String> roles = parseRoles(userRoles);
        if (roles.contains("super_admin") || roles.contains("platform_admin")) {
            return requestedVendorId;
        }
        if (!roles.contains("vendor_admin")) {
            throw new UnauthorizedException("Caller does not have access audit read permission");
        }
        if (callerVendorId == null) {
            throw new UnauthorizedException("Vendor context required for vendor_admin access audit");
        }
        if (requestedVendorId != null && !callerVendorId.equals(requestedVendorId)) {
            throw new UnauthorizedException("vendor_admin cannot query another vendor access audit");
        }
        return callerVendorId;
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
