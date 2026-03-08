package com.rumal.vendor_service.service;

import com.rumal.vendor_service.client.AccessClient;
import com.rumal.vendor_service.entity.VendorUser;
import com.rumal.vendor_service.entity.VendorUserRole;
import com.rumal.vendor_service.dto.VendorStaffAccessLookupResponse;
import com.rumal.vendor_service.exception.UnauthorizedException;
import com.rumal.vendor_service.exception.ValidationException;
import com.rumal.vendor_service.repo.VendorUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

@Service
@RequiredArgsConstructor
public class VendorSelfAccessScopeService {

    private static final String VENDOR_SETTINGS_MANAGE = "vendor.settings.manage";
    private static final String VENDOR_ORDERS_MANAGE = "vendor.orders.manage";

    private final AccessClient accessClient;
    private final VendorUserRepository vendorUserRepository;

    public void assertCanViewVendor(String userSub, String userRoles, String internalAuth, UUID vendorIdHint) {
        resolveVendorIdForView(userSub, userRoles, internalAuth, vendorIdHint);
    }

    public void assertCanManageVendorSettings(String userSub, String userRoles, String internalAuth, UUID vendorIdHint) {
        resolveVendorIdForSettingsManage(userSub, userRoles, internalAuth, vendorIdHint);
    }

    public void assertCanManageVendorOrders(String userSub, String userRoles, String internalAuth, UUID vendorIdHint) {
        resolveVendorIdForOrderManage(userSub, userRoles, internalAuth, vendorIdHint);
    }

    public UUID resolveVendorIdForView(String userSub, String userRoles, String internalAuth, UUID vendorIdHint) {
        Set<String> roles = parseRoles(userRoles);
        if (roles.contains("vendor_admin")) {
            return resolveVendorAdminVendorId(
                    userSub,
                    vendorIdHint,
                    membership -> true,
                    "vendor_admin does not have an active vendor membership"
            );
        }
        return resolveVendorIdByPermission(userSub, internalAuth, vendorIdHint, PermissionMode.VIEW_ANY);
    }

    public UUID resolveVendorIdForSettingsManage(String userSub, String userRoles, String internalAuth, UUID vendorIdHint) {
        Set<String> roles = parseRoles(userRoles);
        if (roles.contains("vendor_admin")) {
            return resolveVendorAdminVendorId(
                    userSub,
                    vendorIdHint,
                    membership -> true,
                    "vendor_admin does not have an active vendor membership"
            );
        }
        return resolveVendorIdByPermission(userSub, internalAuth, vendorIdHint, PermissionMode.SETTINGS_MANAGE);
    }

    public UUID resolveVendorIdForOrderManage(String userSub, String userRoles, String internalAuth, UUID vendorIdHint) {
        Set<String> roles = parseRoles(userRoles);
        if (roles.contains("vendor_admin")) {
            return resolveVendorAdminVendorId(
                    userSub,
                    vendorIdHint,
                    membership -> true,
                    "vendor_admin does not have an active vendor membership"
            );
        }
        return resolveVendorIdByPermission(userSub, internalAuth, vendorIdHint, PermissionMode.ORDERS_MANAGE);
    }

    public UUID resolveVendorIdForOwner(String userSub, String userRoles, UUID vendorIdHint) {
        Set<String> roles = parseRoles(userRoles);
        if (!roles.contains("vendor_admin")) {
            throw new UnauthorizedException("vendor_admin role required");
        }
        return resolveVendorAdminVendorId(
                userSub,
                vendorIdHint,
                membership -> membership.getRole() == VendorUserRole.OWNER,
                "vendor_admin does not own an active vendor membership"
        );
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

    private UUID resolveVendorAdminVendorId(
            String userSub,
            UUID vendorIdHint,
            Predicate<VendorUser> membershipFilter,
            String emptyMessage
    ) {
        String normalizedSub = requireUserSub(userSub);
        List<VendorUser> memberships = vendorUserRepository.findAccessibleMembershipsByKeycloakUser(normalizedSub);
        Set<UUID> vendorIds = new LinkedHashSet<>();
        for (VendorUser membership : memberships) {
            if (membership == null || membership.getVendor() == null || membership.getVendor().getId() == null) {
                continue;
            }
            if (!membershipFilter.test(membership)) {
                continue;
            }
            vendorIds.add(membership.getVendor().getId());
        }
        if (vendorIds.isEmpty()) {
            throw new UnauthorizedException(emptyMessage);
        }
        if (vendorIdHint != null) {
            if (!vendorIds.contains(vendorIdHint)) {
                throw new UnauthorizedException("vendor_admin cannot access another vendor");
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
