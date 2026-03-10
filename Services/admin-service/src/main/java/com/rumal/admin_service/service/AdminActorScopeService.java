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

    private static final String ROLE_SUPER_ADMIN = "super_admin";
    private static final String ROLE_PLATFORM_STAFF = "platform_staff";
    private static final String ROLE_VENDOR_STAFF = "vendor_staff";
    private static final String ROLE_VENDOR_ADMIN = "vendor_admin";
    private static final String VENDOR_ID_FIELD = "vendorId";
    private static final String PERMISSIONS_FIELD = "permissions";
    public static final String PLATFORM_PRODUCTS_MANAGE = "platform.products.manage";
    public static final String PLATFORM_CATEGORIES_MANAGE = "platform.categories.manage";
    public static final String PLATFORM_ORDERS_READ = "platform.orders.read";
    public static final String PLATFORM_ORDERS_MANAGE = "platform.orders.manage";
    public static final String PLATFORM_POSTERS_MANAGE = "platform.posters.manage";
    public static final String PLATFORM_PROMOTIONS_MANAGE = "platform.promotions.manage";
    public static final String PLATFORM_REVIEWS_MANAGE = "platform.reviews.manage";
    public static final String PLATFORM_INVENTORY_MANAGE = "platform.inventory.manage";
    public static final String PLATFORM_VENDORS_READ = "platform.vendors.read";
    public static final String PLATFORM_VENDORS_MANAGE = "platform.vendors.manage";

    public static final String VENDOR_PRODUCTS_MANAGE = "vendor.products.manage";
    public static final String VENDOR_PROMOTIONS_MANAGE = "vendor.promotions.manage";
    public static final String VENDOR_INVENTORY_MANAGE = "vendor.inventory.manage";
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
        if (roles.contains(ROLE_SUPER_ADMIN)) {
            return requestedVendorId;
        }
        if (roles.contains(ROLE_PLATFORM_STAFF)) {
            Set<String> platformPermissions = resolvePlatformPermissionsForUser(userSub, internalAuth);
            if (!platformPermissions.contains(PLATFORM_ORDERS_READ) && !platformPermissions.contains(PLATFORM_ORDERS_MANAGE)) {
                throw new UnauthorizedException("platform_staff does not have order access permission");
            }
            return requestedVendorId;
        }
        if (roles.contains(ROLE_VENDOR_STAFF)) {
            return resolveVendorScopedIdForVendorStaff(userSub, requestedVendorId, internalAuth);
        }
        if (!roles.contains(ROLE_VENDOR_ADMIN)) {
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
        if (roles.contains(ROLE_SUPER_ADMIN)) {
            return;
        }
        if (!roles.contains(ROLE_PLATFORM_STAFF)) {
            throw new UnauthorizedException("Caller does not have poster admin access");
        }
        Set<String> platformPermissions = resolvePlatformPermissionsForUser(userSub, internalAuth);
        if (!platformPermissions.contains(PLATFORM_POSTERS_MANAGE)) {
            throw new UnauthorizedException("platform_staff does not have poster management permission");
        }
    }

    public void assertCanReadVendors(String userSub, String userRolesHeader, String internalAuth) {
        Set<String> roles = parseRoles(userRolesHeader);
        if (roles.contains(ROLE_SUPER_ADMIN)) {
            return;
        }
        if (!roles.contains(ROLE_PLATFORM_STAFF)) {
            throw new UnauthorizedException("Caller does not have vendor read access");
        }
        Set<String> platformPermissions = resolvePlatformPermissionsForUser(userSub, internalAuth);
        if (!platformPermissions.contains(PLATFORM_VENDORS_READ)
                && !platformPermissions.contains(PLATFORM_VENDORS_MANAGE)) {
            throw new UnauthorizedException("platform_staff does not have vendor read permission");
        }
    }

    public void assertCanManageVendors(String userSub, String userRolesHeader, String internalAuth) {
        Set<String> roles = parseRoles(userRolesHeader);
        if (roles.contains(ROLE_SUPER_ADMIN)) {
            return;
        }
        if (!roles.contains(ROLE_PLATFORM_STAFF)) {
            throw new UnauthorizedException("Caller does not have vendor management access");
        }
        Set<String> platformPermissions = resolvePlatformPermissionsForUser(userSub, internalAuth);
        if (!platformPermissions.contains(PLATFORM_VENDORS_MANAGE)) {
            throw new UnauthorizedException("platform_staff does not have vendor management permission");
        }
    }

    public void assertCanSearchKeycloakUsers(String userRolesHeader) {
        Set<String> roles = parseRoles(userRolesHeader);
        if (roles.contains(ROLE_SUPER_ADMIN) || roles.contains(ROLE_VENDOR_ADMIN)) {
            return;
        }
        throw new UnauthorizedException("Caller does not have Keycloak user search access");
    }

    public boolean hasRole(String userRolesHeader, String role) {
        return parseRoles(userRolesHeader).contains(role == null ? "" : role.trim().toLowerCase(Locale.ROOT));
    }

    public void assertHasRole(String userRolesHeader, String... requiredRoles) {
        Set<String> roles = parseRoles(userRolesHeader);
        for (String required : requiredRoles) {
            if (roles.contains(required.trim().toLowerCase(Locale.ROOT))) {
                return;
            }
        }
        throw new UnauthorizedException("Caller does not have required role(s)");
    }

    public void assertCanReadOrders(String userSub, String userRolesHeader, String internalAuth) {
        Set<String> roles = parseRoles(userRolesHeader);
        if (roles.contains(ROLE_SUPER_ADMIN)) {
            return;
        }
        if (!roles.contains(ROLE_PLATFORM_STAFF)) {
            throw new UnauthorizedException("Caller does not have order read access");
        }
        Set<String> platformPermissions = resolvePlatformPermissionsForUser(userSub, internalAuth);
        if (!platformPermissions.contains(PLATFORM_ORDERS_READ) && !platformPermissions.contains(PLATFORM_ORDERS_MANAGE)) {
            throw new UnauthorizedException("platform_staff does not have order read permission");
        }
    }

    public void assertCanAccessOrderExport(
            String userSub,
            String userRolesHeader,
            UUID exportVendorId,
            String internalAuth
    ) {
        Set<String> roles = parseRoles(userRolesHeader);
        if (roles.contains(ROLE_SUPER_ADMIN)) {
            return;
        }
        if (roles.contains(ROLE_PLATFORM_STAFF)) {
            assertCanReadOrders(userSub, userRolesHeader, internalAuth);
            return;
        }
        if (exportVendorId == null) {
            throw new UnauthorizedException("Vendor-scoped users cannot access global order exports");
        }
        resolveScopedVendorIdForOrderAccess(userSub, userRolesHeader, exportVendorId, internalAuth);
    }

    public void assertCanManageOrders(String userSub, String userRolesHeader, String internalAuth) {
        Set<String> roles = parseRoles(userRolesHeader);
        if (roles.contains(ROLE_SUPER_ADMIN)) {
            return;
        }
        if (!roles.contains(ROLE_PLATFORM_STAFF)) {
            throw new UnauthorizedException("Caller does not have order management access");
        }
        Set<String> platformPermissions = resolvePlatformPermissionsForUser(userSub, internalAuth);
        if (!platformPermissions.contains(PLATFORM_ORDERS_MANAGE)) {
            throw new UnauthorizedException("platform_staff does not have order management permission");
        }
    }

    public void assertCanUpdateOrderStatus(String userSub, String userRolesHeader, UUID orderId, String internalAuth) {
        Set<String> roles = parseRoles(userRolesHeader);
        if (roles.contains(ROLE_SUPER_ADMIN)) {
            return;
        }
        if (roles.contains(ROLE_PLATFORM_STAFF)) {
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

        if (roles.contains(ROLE_VENDOR_ADMIN)) {
            Set<UUID> vendorIds = resolveVendorIdsForUser(userSub.trim(), internalAuth);
            if (!vendorIds.contains(orderVendorId)) {
                throw new UnauthorizedException("vendor_admin cannot update status of another vendor's order");
            }
            return;
        }
        if (roles.contains(ROLE_VENDOR_STAFF)) {
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
        if (roles.contains(ROLE_SUPER_ADMIN)) {
            return;
        }
        if (roles.contains(ROLE_PLATFORM_STAFF)) {
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

        if (roles.contains(ROLE_VENDOR_ADMIN)) {
            Set<UUID> vendorIds = resolveVendorIdsForUser(userSub.trim(), internalAuth);
            if (!vendorIds.contains(orderVendorId)) {
                throw new UnauthorizedException("vendor_admin cannot access another vendor's order history");
            }
            return;
        }
        if (roles.contains(ROLE_VENDOR_STAFF)) {
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
        if (roles.contains(ROLE_SUPER_ADMIN)) {
            return;
        }
        if (roles.contains(ROLE_PLATFORM_STAFF)) {
            assertCanManageOrders(userSub, userRolesHeader, internalAuth);
            return;
        }
        if (!StringUtils.hasText(userSub)) {
            throw new UnauthorizedException("Missing authenticated user subject");
        }
        UUID vendorId = resolveVendorIdForVendorOrder(vendorOrderId, internalAuth);
        if (roles.contains(ROLE_VENDOR_ADMIN)) {
            Set<UUID> vendorIds = resolveVendorIdsForUser(userSub.trim(), internalAuth);
            if (!vendorIds.contains(vendorId)) {
                throw new UnauthorizedException("vendor_admin cannot update another vendor's sub-order");
            }
            return;
        }
        if (roles.contains(ROLE_VENDOR_STAFF)) {
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
        if (roles.contains(ROLE_SUPER_ADMIN)) {
            return;
        }
        if (roles.contains(ROLE_PLATFORM_STAFF)) {
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
        if (roles.contains(ROLE_VENDOR_ADMIN)) {
            Set<UUID> vendorIds = resolveVendorIdsForUser(userSub.trim(), internalAuth);
            if (!vendorIds.contains(vendorId)) {
                throw new UnauthorizedException("vendor_admin cannot access another vendor's sub-order history");
            }
            return;
        }
        if (roles.contains(ROLE_VENDOR_STAFF)) {
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
        if (roles.contains(ROLE_SUPER_ADMIN)) {
            return requestedVendorId;
        }
        if (!roles.contains(ROLE_VENDOR_ADMIN)) {
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
        boolean superAdmin = roles.contains(ROLE_SUPER_ADMIN);
        boolean platformStaff = roles.contains(ROLE_PLATFORM_STAFF);
        boolean vendorAdmin = roles.contains(ROLE_VENDOR_ADMIN);
        boolean vendorStaff = roles.contains(ROLE_VENDOR_STAFF);
        String normalizedUserSub = StringUtils.hasText(userSub) ? userSub.trim() : null;

        Set<String> platformPermissions = resolveCapabilitiesPlatformPermissions(
                superAdmin,
                platformStaff,
                normalizedUserSub,
                internalAuth
        );
        List<Map<String, Object>> vendorMemberships = loadVendorMemberships(
                superAdmin,
                vendorAdmin,
                normalizedUserSub,
                internalAuth
        );
        List<Map<String, Object>> vendorStaffAccess = loadVendorStaffAccess(
                superAdmin,
                vendorStaff,
                normalizedUserSub,
                internalAuth
        );
        Set<String> vendorPermissions = extractActiveVendorPermissionCodes(vendorStaffAccess);
        CapabilityFlags capabilityFlags = resolveCapabilityFlags(
                superAdmin,
                vendorAdmin,
                vendorStaff,
                platformPermissions,
                vendorPermissions
        );

        return new AdminCapabilitiesResponse(
                superAdmin,
                platformStaff,
                vendorAdmin,
                vendorStaff,
                platformPermissions,
                vendorMemberships,
                vendorStaffAccess,
                capabilityFlags.canManageOrders(),
                capabilityFlags.canManageProducts(),
                capabilityFlags.canManageCategories(),
                capabilityFlags.canManagePosters(),
                capabilityFlags.canManageVendors(),
                capabilityFlags.canManageInventory(),
                capabilityFlags.canManagePromotions(),
                capabilityFlags.canManageReviews()
        );
    }

    private CapabilityFlags resolveCapabilityFlags(
            boolean superAdmin,
            boolean vendorAdmin,
            boolean vendorStaff,
            Set<String> platformPermissions,
            Set<String> vendorPermissions
    ) {
        boolean vendorStaffCanManageOrders = hasAnyPermission(vendorPermissions, VENDOR_ORDERS_READ, VENDOR_ORDERS_MANAGE);
        boolean vendorStaffCanManageProducts = vendorPermissions.contains(VENDOR_PRODUCTS_MANAGE);
        boolean vendorStaffCanManagePromotions = vendorPermissions.contains(VENDOR_PROMOTIONS_MANAGE);
        boolean vendorStaffCanManageInventory = vendorPermissions.contains(VENDOR_INVENTORY_MANAGE)
                || vendorStaffCanManageProducts;

        return new CapabilityFlags(
                canManageOrders(superAdmin, vendorAdmin, vendorStaff, vendorStaffCanManageOrders, platformPermissions),
                canManageProducts(superAdmin, vendorAdmin, vendorStaff, vendorStaffCanManageProducts, platformPermissions),
                hasPlatformAccess(superAdmin, platformPermissions, PLATFORM_CATEGORIES_MANAGE),
                hasPlatformAccess(superAdmin, platformPermissions, PLATFORM_POSTERS_MANAGE),
                hasPlatformAccess(superAdmin, platformPermissions, PLATFORM_VENDORS_MANAGE),
                canManageInventory(superAdmin, vendorAdmin, vendorStaff, vendorStaffCanManageInventory, platformPermissions),
                canManagePromotions(superAdmin, vendorAdmin, vendorStaff, vendorStaffCanManagePromotions, platformPermissions),
                hasPlatformAccess(superAdmin, platformPermissions, PLATFORM_REVIEWS_MANAGE)
        );
    }

    private boolean hasAnyPermission(Set<String> permissions, String firstPermission, String secondPermission) {
        return permissions.contains(firstPermission) || permissions.contains(secondPermission);
    }

    private boolean canManageOrders(
            boolean superAdmin,
            boolean vendorAdmin,
            boolean vendorStaff,
            boolean vendorStaffCanManageOrders,
            Set<String> platformPermissions
    ) {
        return superAdmin
                || vendorAdmin
                || (vendorStaff && vendorStaffCanManageOrders)
                || hasAnyPermission(platformPermissions, PLATFORM_ORDERS_READ, PLATFORM_ORDERS_MANAGE);
    }

    private boolean canManageProducts(
            boolean superAdmin,
            boolean vendorAdmin,
            boolean vendorStaff,
            boolean vendorStaffCanManageProducts,
            Set<String> platformPermissions
    ) {
        return superAdmin
                || vendorAdmin
                || (vendorStaff && vendorStaffCanManageProducts)
                || platformPermissions.contains(PLATFORM_PRODUCTS_MANAGE);
    }

    private boolean canManageInventory(
            boolean superAdmin,
            boolean vendorAdmin,
            boolean vendorStaff,
            boolean vendorStaffCanManageInventory,
            Set<String> platformPermissions
    ) {
        return superAdmin
                || vendorAdmin
                || (vendorStaff && vendorStaffCanManageInventory)
                || hasAnyPermission(platformPermissions, PLATFORM_INVENTORY_MANAGE, PLATFORM_PRODUCTS_MANAGE);
    }

    private boolean canManagePromotions(
            boolean superAdmin,
            boolean vendorAdmin,
            boolean vendorStaff,
            boolean vendorStaffCanManagePromotions,
            Set<String> platformPermissions
    ) {
        return superAdmin
                || vendorAdmin
                || (vendorStaff && vendorStaffCanManagePromotions)
                || platformPermissions.contains(PLATFORM_PROMOTIONS_MANAGE);
    }

    private boolean hasPlatformAccess(boolean superAdmin, Set<String> platformPermissions, String requiredPermission) {
        return superAdmin || platformPermissions.contains(requiredPermission);
    }

    private Set<UUID> resolveVendorIdsForUser(String keycloakUserId, String internalAuth) {
        List<Map<String, Object>> memberships = adminVendorService.listAccessibleVendorMembershipsByKeycloakUser(keycloakUserId, internalAuth);
        Set<UUID> vendorIds = new LinkedHashSet<>();
        for (Map<String, Object> membership : memberships) {
            Object rawVendorId = membership.get(VENDOR_ID_FIELD);
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
            UUID vendorId = extractVendorIdForOrderReadableAccess(row);
            if (vendorId != null) {
                allowedVendorIds.add(vendorId);
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
            UUID vendorId = extractVendorIdForPermission(row, VENDOR_ORDERS_MANAGE);
            if (vendorId != null) {
                allowedVendorIds.add(vendorId);
            }
        }
        return Set.copyOf(allowedVendorIds);
    }

    private Set<UUID> resolveVendorIdsForVendorStaffWithOrderReadPermission(String userSub, String internalAuth) {
        List<Map<String, Object>> accessRows = adminAccessService.listVendorStaffAccessByKeycloakUser(userSub, internalAuth);
        Set<UUID> allowedVendorIds = new LinkedHashSet<>();
        for (Map<String, Object> row : accessRows) {
            UUID vendorId = extractVendorIdForOrderReadableAccess(row);
            if (vendorId != null) {
                allowedVendorIds.add(vendorId);
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
        boolean active = activeRaw instanceof Boolean activeValue && activeValue;
        if (!active) {
            return Set.of();
        }
        return parseStringSet(access.get(PERMISSIONS_FIELD));
    }

    private Set<String> parseRoles(String rolesHeader) {
        if (!StringUtils.hasText(rolesHeader)) {
            return Set.of();
        }
        Set<String> roles = new LinkedHashSet<>();
        for (String part : rolesHeader.split(",")) {
            String normalized = normalizeRole(part);
            if (!normalized.isEmpty()) {
                roles.add(normalized);
            }
        }
        return roles;
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

    private Set<String> extractActiveVendorPermissionCodes(List<Map<String, Object>> accessRows) {
        if (accessRows == null || accessRows.isEmpty()) {
            return Set.of();
        }
        Set<String> permissions = new LinkedHashSet<>();
        for (Map<String, Object> row : accessRows) {
            if (row == null || !isTruthy(row.get("active"))) {
                continue;
            }
            for (String permission : parseStringSet(row.get(PERMISSIONS_FIELD))) {
                if (StringUtils.hasText(permission)) {
                    permissions.add(permission.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return Set.copyOf(permissions);
    }

    private Set<String> resolveCapabilitiesPlatformPermissions(
            boolean superAdmin,
            boolean platformStaff,
            String userSub,
            String internalAuth
    ) {
        if (superAdmin) {
            return Set.of(
                    PLATFORM_PRODUCTS_MANAGE,
                    PLATFORM_CATEGORIES_MANAGE,
                    PLATFORM_ORDERS_READ,
                    PLATFORM_ORDERS_MANAGE,
                    PLATFORM_POSTERS_MANAGE,
                    PLATFORM_PROMOTIONS_MANAGE,
                    PLATFORM_REVIEWS_MANAGE
            );
        }
        if (platformStaff) {
            return resolvePlatformPermissionsForUser(userSub, internalAuth);
        }
        return Set.of();
    }

    private List<Map<String, Object>> loadVendorMemberships(
            boolean superAdmin,
            boolean vendorAdmin,
            String userSub,
            String internalAuth
    ) {
        if ((superAdmin || vendorAdmin) && StringUtils.hasText(userSub)) {
            return adminVendorService.listAccessibleVendorMembershipsByKeycloakUser(userSub, internalAuth);
        }
        return List.of();
    }

    private List<Map<String, Object>> loadVendorStaffAccess(
            boolean superAdmin,
            boolean vendorStaff,
            String userSub,
            String internalAuth
    ) {
        if ((superAdmin || vendorStaff) && StringUtils.hasText(userSub)) {
            return adminAccessService.listVendorStaffAccessByKeycloakUser(userSub, internalAuth);
        }
        return List.of();
    }

    private UUID extractVendorIdForOrderReadableAccess(Map<String, Object> row) {
        Set<String> permissions = parseStringSet(row.get(PERMISSIONS_FIELD));
        if (!permissions.contains(VENDOR_ORDERS_READ) && !permissions.contains(VENDOR_ORDERS_MANAGE)) {
            return null;
        }
        return parseVendorIdSafely(row);
    }

    private UUID extractVendorIdForPermission(Map<String, Object> row, String requiredPermission) {
        Set<String> permissions = parseStringSet(row.get(PERMISSIONS_FIELD));
        if (!permissions.contains(requiredPermission)) {
            return null;
        }
        return parseVendorIdSafely(row);
    }

    private UUID parseVendorIdSafely(Map<String, Object> row) {
        Object rawVendorId = row.get(VENDOR_ID_FIELD);
        if (rawVendorId == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(rawVendorId));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean isTruthy(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value == null) {
            return false;
        }
        return Boolean.parseBoolean(String.valueOf(value));
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

    private record CapabilityFlags(
            boolean canManageOrders,
            boolean canManageProducts,
            boolean canManageCategories,
            boolean canManagePosters,
            boolean canManageVendors,
            boolean canManageInventory,
            boolean canManagePromotions,
            boolean canManageReviews
    ) {
    }
}
