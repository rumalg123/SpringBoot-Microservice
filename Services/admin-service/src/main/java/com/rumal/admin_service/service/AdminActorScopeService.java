package com.rumal.admin_service.service;

import com.rumal.admin_service.dto.AdminCapabilitiesResponse;
import com.rumal.admin_service.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminActorScopeService {

    public static final String PLATFORM_PRODUCTS_MANAGE = "platform.products.manage";
    public static final String PLATFORM_CATEGORIES_MANAGE = "platform.categories.manage";
    public static final String PLATFORM_ORDERS_READ = "platform.orders.read";
    public static final String PLATFORM_ORDERS_MANAGE = "platform.orders.manage";
    public static final String PLATFORM_POSTERS_MANAGE = "platform.posters.manage";

    public static final String VENDOR_PRODUCTS_MANAGE = "vendor.products.manage";
    public static final String VENDOR_ORDERS_READ = "vendor.orders.read";
    public static final String VENDOR_ORDERS_MANAGE = "vendor.orders.manage";

    private final AdminVendorService adminVendorService;
    private final AdminAccessService adminAccessService;
    private final AdminOrderService adminOrderService;

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
        if (roles.contains("platform_staff")) {
            Set<String> platformPermissions = resolvePlatformPermissionsForUser(userSub, internalAuth);
            if (!platformPermissions.contains(PLATFORM_ORDERS_READ) && !platformPermissions.contains(PLATFORM_ORDERS_MANAGE)) {
                throw new UnauthorizedException("platform_staff does not have order access permission");
            }
            return requestedVendorId;
        }
        if (roles.contains("vendor_staff")) {
            return resolveVendorScopedIdForVendorStaff(userSub, requestedVendorId, internalAuth);
        }
        if (!roles.contains("vendor_admin")) {
            throw new UnauthorizedException("Caller does not have admin order access");
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

    public void assertCanManagePosters(String userSub, String userRolesHeader, String internalAuth) {
        Set<String> roles = parseRoles(userRolesHeader);
        if (roles.contains("super_admin")) {
            return;
        }
        if (!roles.contains("platform_staff")) {
            throw new UnauthorizedException("Caller does not have poster admin access");
        }
        Set<String> platformPermissions = resolvePlatformPermissionsForUser(userSub, internalAuth);
        if (!platformPermissions.contains(PLATFORM_POSTERS_MANAGE)) {
            throw new UnauthorizedException("platform_staff does not have poster management permission");
        }
    }

    public void assertCanSearchKeycloakUsers(String userRolesHeader) {
        Set<String> roles = parseRoles(userRolesHeader);
        if (roles.contains("super_admin") || roles.contains("vendor_admin")) {
            return;
        }
        throw new UnauthorizedException("Caller does not have Keycloak user search access");
    }

    public boolean hasRole(String userRolesHeader, String role) {
        return parseRoles(userRolesHeader).contains(role == null ? "" : role.trim().toLowerCase(Locale.ROOT));
    }

    public void assertHasRole(String userSub, String userRolesHeader, String... requiredRoles) {
        Set<String> roles = parseRoles(userRolesHeader);
        for (String required : requiredRoles) {
            if (roles.contains(required.trim().toLowerCase(Locale.ROOT))) {
                return;
            }
        }
        throw new UnauthorizedException("Caller does not have required role(s)");
    }

    public void assertCanManageOrders(String userSub, String userRolesHeader, String internalAuth) {
        Set<String> roles = parseRoles(userRolesHeader);
        if (roles.contains("super_admin")) {
            return;
        }
        if (!roles.contains("platform_staff")) {
            throw new UnauthorizedException("Caller does not have order management access");
        }
        Set<String> platformPermissions = resolvePlatformPermissionsForUser(userSub, internalAuth);
        if (!platformPermissions.contains(PLATFORM_ORDERS_MANAGE)) {
            throw new UnauthorizedException("platform_staff does not have order management permission");
        }
    }

    public void assertCanUpdateOrderStatus(String userSub, String userRolesHeader, UUID orderId, String internalAuth) {
        Set<String> roles = parseRoles(userRolesHeader);
        if (roles.contains("super_admin")) {
            return;
        }
        if (roles.contains("platform_staff")) {
            assertCanManageOrders(userSub, userRolesHeader, internalAuth);
            return;
        }
        if (!StringUtils.hasText(userSub)) {
            throw new UnauthorizedException("Missing authenticated user subject");
        }

        Set<UUID> orderVendorIds = adminOrderService.getOrderVendorIds(orderId, internalAuth);
        if (orderVendorIds.isEmpty()) {
            throw new UnauthorizedException("Order vendor ownership could not be resolved");
        }
        if (orderVendorIds.size() > 1) {
            throw new UnauthorizedException("Vendor-scoped users cannot update status of multi-vendor orders");
        }
        UUID orderVendorId = orderVendorIds.iterator().next();

        if (roles.contains("vendor_admin")) {
            Set<UUID> vendorIds = resolveVendorIdsForUser(userSub.trim(), internalAuth);
            if (!vendorIds.contains(orderVendorId)) {
                throw new UnauthorizedException("vendor_admin cannot update status of another vendor's order");
            }
            return;
        }
        if (roles.contains("vendor_staff")) {
            Set<UUID> vendorIds = resolveVendorIdsForVendorStaffWithManagePermission(userSub.trim(), internalAuth);
            if (!vendorIds.contains(orderVendorId)) {
                throw new UnauthorizedException("vendor_staff does not have order management permission for this vendor");
            }
            return;
        }
        throw new UnauthorizedException("Caller does not have order status management access");
    }

    public void assertCanReadOrderHistory(String userSub, String userRolesHeader, UUID orderId, String internalAuth) {
        Set<String> roles = parseRoles(userRolesHeader);
        if (roles.contains("super_admin")) {
            return;
        }
        if (roles.contains("platform_staff")) {
            Set<String> platformPermissions = resolvePlatformPermissionsForUser(userSub, internalAuth);
            if (!platformPermissions.contains(PLATFORM_ORDERS_READ) && !platformPermissions.contains(PLATFORM_ORDERS_MANAGE)) {
                throw new UnauthorizedException("platform_staff does not have order access permission");
            }
            return;
        }
        if (!StringUtils.hasText(userSub)) {
            throw new UnauthorizedException("Missing authenticated user subject");
        }

        Set<UUID> orderVendorIds = adminOrderService.getOrderVendorIds(orderId, internalAuth);
        if (orderVendorIds.isEmpty()) {
            throw new UnauthorizedException("Order vendor ownership could not be resolved");
        }
        if (orderVendorIds.size() > 1) {
            throw new UnauthorizedException("Vendor-scoped users cannot access history of multi-vendor orders");
        }
        UUID orderVendorId = orderVendorIds.iterator().next();

        if (roles.contains("vendor_admin")) {
            Set<UUID> vendorIds = resolveVendorIdsForUser(userSub.trim(), internalAuth);
            if (!vendorIds.contains(orderVendorId)) {
                throw new UnauthorizedException("vendor_admin cannot access another vendor's order history");
            }
            return;
        }
        if (roles.contains("vendor_staff")) {
            Set<UUID> vendorIds = resolveVendorIdsForVendorStaffWithOrderReadPermission(userSub.trim(), internalAuth);
            if (!vendorIds.contains(orderVendorId)) {
                throw new UnauthorizedException("vendor_staff does not have order access for this vendor");
            }
            return;
        }
        throw new UnauthorizedException("Caller does not have order history access");
    }

    public void assertCanUpdateVendorOrderStatus(String userSub, String userRolesHeader, UUID vendorOrderId, String internalAuth) {
        Set<String> roles = parseRoles(userRolesHeader);
        if (roles.contains("super_admin")) {
            return;
        }
        if (roles.contains("platform_staff")) {
            assertCanManageOrders(userSub, userRolesHeader, internalAuth);
            return;
        }
        if (!StringUtils.hasText(userSub)) {
            throw new UnauthorizedException("Missing authenticated user subject");
        }
        UUID vendorId = resolveVendorIdForVendorOrder(vendorOrderId, internalAuth);
        if (roles.contains("vendor_admin")) {
            Set<UUID> vendorIds = resolveVendorIdsForUser(userSub.trim(), internalAuth);
            if (!vendorIds.contains(vendorId)) {
                throw new UnauthorizedException("vendor_admin cannot update another vendor's sub-order");
            }
            return;
        }
        if (roles.contains("vendor_staff")) {
            Set<UUID> vendorIds = resolveVendorIdsForVendorStaffWithManagePermission(userSub.trim(), internalAuth);
            if (!vendorIds.contains(vendorId)) {
                throw new UnauthorizedException("vendor_staff does not have order management permission for this vendor");
            }
            return;
        }
        throw new UnauthorizedException("Caller does not have vendor order management access");
    }

    public void assertCanReadVendorOrderHistory(String userSub, String userRolesHeader, UUID vendorOrderId, String internalAuth) {
        Set<String> roles = parseRoles(userRolesHeader);
        if (roles.contains("super_admin")) {
            return;
        }
        if (roles.contains("platform_staff")) {
            Set<String> platformPermissions = resolvePlatformPermissionsForUser(userSub, internalAuth);
            if (!platformPermissions.contains(PLATFORM_ORDERS_READ) && !platformPermissions.contains(PLATFORM_ORDERS_MANAGE)) {
                throw new UnauthorizedException("platform_staff does not have order access permission");
            }
            return;
        }
        if (!StringUtils.hasText(userSub)) {
            throw new UnauthorizedException("Missing authenticated user subject");
        }
        UUID vendorId = resolveVendorIdForVendorOrder(vendorOrderId, internalAuth);
        if (roles.contains("vendor_admin")) {
            Set<UUID> vendorIds = resolveVendorIdsForUser(userSub.trim(), internalAuth);
            if (!vendorIds.contains(vendorId)) {
                throw new UnauthorizedException("vendor_admin cannot access another vendor's sub-order history");
            }
            return;
        }
        if (roles.contains("vendor_staff")) {
            Set<UUID> vendorIds = resolveVendorIdsForVendorStaffWithOrderReadPermission(userSub.trim(), internalAuth);
            if (!vendorIds.contains(vendorId)) {
                throw new UnauthorizedException("vendor_staff does not have order access for this vendor");
            }
            return;
        }
        throw new UnauthorizedException("Caller does not have vendor order history access");
    }

    public UUID resolveScopedVendorIdForVendorStaffManagement(String userSub, String userRolesHeader, UUID requestedVendorId, String internalAuth) {
        Set<String> roles = parseRoles(userRolesHeader);
        if (roles.contains("super_admin")) {
            return requestedVendorId;
        }
        if (!roles.contains("vendor_admin")) {
            throw new UnauthorizedException("Caller does not have vendor staff management access");
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
                throw new UnauthorizedException("vendor_admin cannot manage staff of another vendor");
            }
            return requestedVendorId;
        }
        if (vendorIds.size() == 1) {
            return vendorIds.iterator().next();
        }
        throw new UnauthorizedException("vendorId is required when vendor_admin belongs to multiple vendors");
    }

    public void assertCanManageVendorStaffVendor(String userSub, String userRolesHeader, UUID vendorId, String internalAuth) {
        resolveScopedVendorIdForVendorStaffManagement(userSub, userRolesHeader, vendorId, internalAuth);
    }

    public AdminCapabilitiesResponse describeCapabilities(String userSub, String userRolesHeader, String internalAuth) {
        Set<String> roles = parseRoles(userRolesHeader);
        boolean superAdmin = roles.contains("super_admin");
        boolean platformStaff = roles.contains("platform_staff");
        boolean vendorAdmin = roles.contains("vendor_admin");
        boolean vendorStaff = roles.contains("vendor_staff");

        Set<String> platformPermissions = superAdmin
                ? Set.of(PLATFORM_PRODUCTS_MANAGE, PLATFORM_CATEGORIES_MANAGE, PLATFORM_ORDERS_READ, PLATFORM_ORDERS_MANAGE, PLATFORM_POSTERS_MANAGE)
                : (platformStaff ? resolvePlatformPermissionsForUser(userSub, internalAuth) : Set.of());

        List<Map<String, Object>> vendorMemberships = (superAdmin || vendorAdmin) && StringUtils.hasText(userSub)
                ? adminVendorService.listAccessibleVendorMembershipsByKeycloakUser(userSub.trim(), internalAuth)
                : List.of();
        List<Map<String, Object>> vendorStaffAccess = (superAdmin || vendorStaff) && StringUtils.hasText(userSub)
                ? adminAccessService.listVendorStaffAccessByKeycloakUser(userSub.trim(), internalAuth)
                : List.of();

        boolean canManageOrders = superAdmin || vendorAdmin || vendorStaff
                || platformPermissions.contains(PLATFORM_ORDERS_READ)
                || platformPermissions.contains(PLATFORM_ORDERS_MANAGE);
        boolean canManageProducts = superAdmin || vendorAdmin || vendorStaff
                || platformPermissions.contains(PLATFORM_PRODUCTS_MANAGE);
        boolean canManageCategories = superAdmin || platformPermissions.contains(PLATFORM_CATEGORIES_MANAGE);
        boolean canManagePosters = superAdmin || platformPermissions.contains(PLATFORM_POSTERS_MANAGE);

        return new AdminCapabilitiesResponse(
                superAdmin,
                platformStaff,
                vendorAdmin,
                vendorStaff,
                platformPermissions,
                vendorMemberships,
                vendorStaffAccess,
                canManageOrders,
                canManageProducts,
                canManageCategories,
                canManagePosters,
                superAdmin
        );
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

    private UUID resolveVendorScopedIdForVendorStaff(String userSub, UUID requestedVendorId, String internalAuth) {
        if (!StringUtils.hasText(userSub)) {
            throw new UnauthorizedException("Missing authenticated user subject");
        }
        List<Map<String, Object>> accessRows = adminAccessService.listVendorStaffAccessByKeycloakUser(userSub.trim(), internalAuth);
        Set<UUID> allowedVendorIds = new LinkedHashSet<>();
        for (Map<String, Object> row : accessRows) {
            Set<String> permissions = parseStringSet(row.get("permissions"));
            boolean canReadOrders = permissions.contains(VENDOR_ORDERS_READ) || permissions.contains(VENDOR_ORDERS_MANAGE);
            if (!canReadOrders) {
                continue;
            }
            Object rawVendorId = row.get("vendorId");
            if (rawVendorId == null) {
                continue;
            }
            try {
                allowedVendorIds.add(UUID.fromString(String.valueOf(rawVendorId)));
            } catch (IllegalArgumentException ignored) {
                // ignore malformed downstream rows
            }
        }

        if (allowedVendorIds.isEmpty()) {
            throw new UnauthorizedException("No vendor order access found for vendor_staff user");
        }
        if (requestedVendorId != null) {
            if (!allowedVendorIds.contains(requestedVendorId)) {
                throw new UnauthorizedException("vendor_staff cannot access orders of another vendor");
            }
            return requestedVendorId;
        }
        if (allowedVendorIds.size() == 1) {
            return allowedVendorIds.iterator().next();
        }
        throw new UnauthorizedException("vendorId is required when vendor_staff has access to multiple vendors");
    }

    private Set<UUID> resolveVendorIdsForVendorStaffWithManagePermission(String userSub, String internalAuth) {
        List<Map<String, Object>> accessRows = adminAccessService.listVendorStaffAccessByKeycloakUser(userSub, internalAuth);
        Set<UUID> allowedVendorIds = new LinkedHashSet<>();
        for (Map<String, Object> row : accessRows) {
            Set<String> permissions = parseStringSet(row.get("permissions"));
            if (!permissions.contains(VENDOR_ORDERS_MANAGE)) {
                continue;
            }
            Object rawVendorId = row.get("vendorId");
            if (rawVendorId == null) {
                continue;
            }
            try {
                allowedVendorIds.add(UUID.fromString(String.valueOf(rawVendorId)));
            } catch (IllegalArgumentException ignored) {
                // ignore malformed downstream rows
            }
        }
        return Set.copyOf(allowedVendorIds);
    }

    private Set<UUID> resolveVendorIdsForVendorStaffWithOrderReadPermission(String userSub, String internalAuth) {
        List<Map<String, Object>> accessRows = adminAccessService.listVendorStaffAccessByKeycloakUser(userSub, internalAuth);
        Set<UUID> allowedVendorIds = new LinkedHashSet<>();
        for (Map<String, Object> row : accessRows) {
            Set<String> permissions = parseStringSet(row.get("permissions"));
            if (!permissions.contains(VENDOR_ORDERS_READ) && !permissions.contains(VENDOR_ORDERS_MANAGE)) {
                continue;
            }
            Object rawVendorId = row.get("vendorId");
            if (rawVendorId == null) {
                continue;
            }
            try {
                allowedVendorIds.add(UUID.fromString(String.valueOf(rawVendorId)));
            } catch (IllegalArgumentException ignored) {
                // ignore malformed downstream rows
            }
        }
        return Set.copyOf(allowedVendorIds);
    }

    private UUID resolveVendorIdForVendorOrder(UUID vendorOrderId, String internalAuth) {
        var vendorOrder = adminOrderService.getVendorOrder(vendorOrderId, internalAuth);
        if (vendorOrder == null || vendorOrder.vendorId() == null) {
            throw new UnauthorizedException("Vendor order ownership could not be resolved");
        }
        return vendorOrder.vendorId();
    }

    private Set<String> resolvePlatformPermissionsForUser(String userSub, String internalAuth) {
        if (!StringUtils.hasText(userSub)) {
            throw new UnauthorizedException("Missing authenticated user subject");
        }
        Map<String, Object> access = adminAccessService.getPlatformAccessByKeycloakUser(userSub.trim(), internalAuth);
        Object activeRaw = access.get("active");
        if (!(activeRaw instanceof Boolean active) || !active) {
            return Set.of();
        }
        return parseStringSet(access.get("permissions"));
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

    private Set<String> parseStringSet(Object raw) {
        if (!(raw instanceof Collection<?> items)) {
            return Set.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (Object item : items) {
            if (item == null) {
                continue;
            }
            String value = String.valueOf(item).trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return Set.copyOf(values);
    }
}
