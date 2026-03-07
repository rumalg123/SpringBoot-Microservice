package com.rumal.vendor_service.service;

import com.rumal.vendor_service.client.AccessClient;
import com.rumal.vendor_service.dto.VendorStaffAccessLookupResponse;
import com.rumal.vendor_service.exception.UnauthorizedException;
import com.rumal.vendor_service.exception.ValidationException;
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
public class VendorSelfAccessScopeService {

    private static final String VENDOR_SETTINGS_MANAGE = "vendor.settings.manage";
    private static final String VENDOR_ORDERS_MANAGE = "vendor.orders.manage";

    private final AccessClient accessClient;

    public void assertCanViewVendor(String userSub, String userRoles, String internalAuth, UUID vendorIdHint) {
        Set<String> roles = parseRoles(userRoles);
        if (roles.contains("vendor_admin")) {
            return;
        }
        resolveVendorIdByPermission(userSub, internalAuth, vendorIdHint, PermissionMode.VIEW_ANY);
    }

    public void assertCanManageVendorSettings(String userSub, String userRoles, String internalAuth, UUID vendorIdHint) {
        Set<String> roles = parseRoles(userRoles);
        if (roles.contains("vendor_admin")) {
            return;
        }
        resolveVendorIdByPermission(userSub, internalAuth, vendorIdHint, PermissionMode.SETTINGS_MANAGE);
    }

    public void assertCanManageVendorOrders(String userSub, String userRoles, String internalAuth, UUID vendorIdHint) {
        Set<String> roles = parseRoles(userRoles);
        if (roles.contains("vendor_admin")) {
            return;
        }
        resolveVendorIdByPermission(userSub, internalAuth, vendorIdHint, PermissionMode.ORDERS_MANAGE);
    }

    private UUID resolveVendorIdByPermission(String userSub, String internalAuth, UUID vendorIdHint, PermissionMode mode) {
        String normalizedSub = requireUserSub(userSub);
        List<VendorStaffAccessLookupResponse> accessRows =
                accessClient.listVendorStaffAccessByKeycloakUser(normalizedSub, internalAuth);
        Set<UUID> vendorIds = new LinkedHashSet<>();
        for (VendorStaffAccessLookupResponse row : accessRows) {
            if (row == null || row.vendorId() == null || !row.active()) {
                continue;
            }
            Set<String> permissions = row.permissions() == null ? Set.of() : row.permissions();
            boolean allowed = switch (mode) {
                case VIEW_ANY -> !permissions.isEmpty();
                case SETTINGS_MANAGE -> permissions.contains(VENDOR_SETTINGS_MANAGE);
                case ORDERS_MANAGE -> permissions.contains(VENDOR_ORDERS_MANAGE);
            };
            if (allowed) {
                vendorIds.add(row.vendorId());
            }
        }

        if (vendorIds.isEmpty()) {
            throw switch (mode) {
                case VIEW_ANY -> new UnauthorizedException("vendor_staff access is not configured");
                case SETTINGS_MANAGE -> new UnauthorizedException("vendor_staff does not have vendor settings permission");
                case ORDERS_MANAGE -> new UnauthorizedException("vendor_staff does not have vendor order management permission");
            };
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

    private enum PermissionMode {
        VIEW_ANY,
        SETTINGS_MANAGE,
        ORDERS_MANAGE
    }
}
