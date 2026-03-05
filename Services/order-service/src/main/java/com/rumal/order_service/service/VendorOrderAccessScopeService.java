package com.rumal.order_service.service;

import com.rumal.order_service.client.AccessClient;
import com.rumal.order_service.client.VendorClient;
import com.rumal.order_service.dto.VendorStaffAccessLookupResponse;
import com.rumal.order_service.exception.UnauthorizedException;
import com.rumal.order_service.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VendorOrderAccessScopeService {

    private static final String VENDOR_ORDERS_READ = "vendor.orders.read";
    private static final String VENDOR_ORDERS_MANAGE = "vendor.orders.manage";

    private final AccessClient accessClient;
    private final VendorClient vendorClient;

    public UUID resolveVendorIdForOrderRead(String userSub, String userRoles, String internalAuth, UUID vendorIdHint) {
        return resolveVendorId(userSub, userRoles, internalAuth, vendorIdHint, false);
    }

    public UUID resolveVendorIdForOrderManage(String userSub, String userRoles, String internalAuth, UUID vendorIdHint) {
        return resolveVendorId(userSub, userRoles, internalAuth, vendorIdHint, true);
    }

    private UUID resolveVendorId(
            String userSub,
            String userRoles,
            String internalAuth,
            UUID vendorIdHint,
            boolean requireManagePermission
    ) {
        Set<String> roles = parseRoles(userRoles);
        String normalizedSub = requireUserSub(userSub);

        if (roles.contains("vendor_admin") || roles.contains("super_admin")) {
            return vendorClient.getVendorForUser(normalizedSub, vendorIdHint).id();
        }

        if (!roles.contains("vendor_staff")) {
            throw new UnauthorizedException("Vendor role required");
        }

        List<VendorStaffAccessLookupResponse> accessRows =
                accessClient.listVendorStaffAccessByKeycloakUser(normalizedSub, internalAuth);
        Set<UUID> vendorIds = new LinkedHashSet<>();
        for (VendorStaffAccessLookupResponse row : accessRows) {
            if (row == null || row.vendorId() == null || !row.active()) {
                continue;
            }
            Set<String> permissions = row.permissions() == null ? Set.of() : row.permissions();
            boolean allowed = requireManagePermission
                    ? permissions.contains(VENDOR_ORDERS_MANAGE)
                    : permissions.contains(VENDOR_ORDERS_READ) || permissions.contains(VENDOR_ORDERS_MANAGE);
            if (allowed) {
                vendorIds.add(row.vendorId());
            }
        }
        return resolveVendorIdForVendorStaff(vendorIds, vendorIdHint, requireManagePermission);
    }

    private UUID resolveVendorIdForVendorStaff(Set<UUID> vendorIds, UUID vendorIdHint, boolean requireManagePermission) {
        if (vendorIds.isEmpty()) {
            if (requireManagePermission) {
                throw new UnauthorizedException("vendor_staff does not have order management permission");
            }
            throw new UnauthorizedException("vendor_staff does not have order read permission");
        }
        if (vendorIdHint != null) {
            if (!vendorIds.contains(vendorIdHint)) {
                throw new UnauthorizedException("vendor_staff cannot access another vendor");
            }
            return vendorIdHint;
        }
        if (vendorIds.size() == 1) {
            return vendorIds.iterator().next();
        }
        throw new ValidationException("vendorId is required when user has access to multiple vendors");
    }

    private String requireUserSub(String userSub) {
        if (!StringUtils.hasText(userSub)) {
            throw new UnauthorizedException("Missing authentication header");
        }
        return userSub.trim();
    }

    private Set<String> parseRoles(String rolesHeader) {
        if (!StringUtils.hasText(rolesHeader)) {
            return Set.of();
        }
        Set<String> roles = new LinkedHashSet<>();
        for (String role : rolesHeader.split(",")) {
            String normalized = normalizeRole(role);
            if (!normalized.isEmpty()) {
                roles.add(normalized);
                if ("platform_admin".equals(normalized)) {
                    roles.add("super_admin");
                }
            }
        }
        return Set.copyOf(roles);
    }

    private String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
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
