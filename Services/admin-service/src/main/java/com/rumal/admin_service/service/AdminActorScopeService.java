package com.rumal.admin_service.service;

import com.rumal.admin_service.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminActorScopeService {

    private final AdminVendorService adminVendorService;

    public UUID resolveScopedVendorIdForOrderAccess(
            String userSub,
            String userRolesHeader,
            UUID requestedVendorId,
            String internalAuth
    ) {
        Set<String> roles = parseRoles(userRolesHeader);
        if (roles.contains("super_admin")) {
            return requestedVendorId;
        }
        if (!roles.contains("vendor_admin")) {
            throw new UnauthorizedException("Caller does not have admin access");
        }
        if (!StringUtils.hasText(userSub)) {
            throw new UnauthorizedException("Missing authenticated user subject");
        }

        Set<UUID> vendorIds = resolveVendorIdsForUser(userSub.trim(), internalAuth);
        if (vendorIds.isEmpty()) {
            throw new UnauthorizedException("No active vendor membership found for vendor_admin user");
        }
        if (requestedVendorId != null) {
            if (!vendorIds.contains(requestedVendorId)) {
                throw new UnauthorizedException("vendor_admin cannot access orders of another vendor");
            }
            return requestedVendorId;
        }
        if (vendorIds.size() == 1) {
            return vendorIds.iterator().next();
        }
        throw new UnauthorizedException("vendorId is required when vendor_admin belongs to multiple vendors");
    }

    private Set<UUID> resolveVendorIdsForUser(String keycloakUserId, String internalAuth) {
        List<Map<String, Object>> memberships = adminVendorService.listAccessibleVendorMembershipsByKeycloakUser(keycloakUserId, internalAuth);
        Set<UUID> vendorIds = new LinkedHashSet<>();
        for (Map<String, Object> membership : memberships) {
            Object rawVendorId = membership.get("vendorId");
            if (!(rawVendorId instanceof String raw)) {
                continue;
            }
            try {
                vendorIds.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
                // ignore malformed rows from downstream
            }
        }
        return vendorIds;
    }

    private Set<String> parseRoles(String rolesHeader) {
        if (!StringUtils.hasText(rolesHeader)) {
            return Set.of();
        }
        Set<String> roles = new LinkedHashSet<>();
        for (String part : rolesHeader.split(",")) {
            if (part != null && !part.isBlank()) {
                roles.add(part.trim().toLowerCase(Locale.ROOT));
            }
        }
        return roles;
    }
}
