package com.rumal.inventory_service.service;

import com.rumal.inventory_service.client.AccessClient;
import com.rumal.inventory_service.client.VendorAccessClient;
import com.rumal.inventory_service.dto.PlatformAccessLookupResponse;
import com.rumal.inventory_service.dto.VendorAccessMembershipResponse;
import com.rumal.inventory_service.dto.VendorStaffAccessLookupResponse;
import com.rumal.inventory_service.exception.UnauthorizedException;
import com.rumal.inventory_service.exception.ValidationException;
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
public class AdminInventoryAccessScopeService {

    public static final String PLATFORM_INVENTORY_MANAGE = "platform.inventory.manage";
    public static final String VENDOR_INVENTORY_MANAGE = "vendor.inventory.manage";

    private final AccessClient accessClient;
    private final VendorAccessClient vendorAccessClient;

    public AdminActorScope resolveActorScope(String userSub, String userRolesHeader, String internalAuth) {
        return resolveScope(userSub, userRolesHeader, internalAuth);
    }

    public UUID resolveScopedVendorFilter(AdminActorScope scope, UUID requestedVendorId) {
        if (scope.isPlatformPrivileged()) {
            return requestedVendorId;
        }
        return resolveVendorIdForVendorScopedActor(scope.vendorInventoryVendorIds(), requestedVendorId);
    }

    public void assertCanManageInventory(AdminActorScope scope) {
        if (scope.isPlatformPrivileged()) {
            return;
        }
        if (scope.vendorInventoryVendorIds().isEmpty()) {
            throw new UnauthorizedException("Caller does not have inventory management access");
        }
    }

    public void assertCanManageWarehouse(AdminActorScope scope, UUID warehouseVendorId) {
        if (scope.isPlatformPrivileged()) {
            return;
        }
        if (warehouseVendorId == null) {
            throw new UnauthorizedException("Vendor-scoped user cannot manage platform warehouses");
        }
        if (!scope.vendorInventoryVendorIds().contains(warehouseVendorId)) {
            throw new UnauthorizedException("Vendor-scoped user cannot manage warehouses of another vendor");
        }
    }

    public void assertCanManageStockItem(AdminActorScope scope, UUID stockItemVendorId) {
        if (scope.isPlatformPrivileged()) {
            return;
        }
        if (stockItemVendorId == null || !scope.vendorInventoryVendorIds().contains(stockItemVendorId)) {
            throw new UnauthorizedException("Vendor-scoped user cannot manage stock of another vendor");
        }
    }

    private AdminActorScope resolveScope(String userSub, String userRolesHeader, String internalAuth) {
        Set<String> roles = parseRoles(userRolesHeader);
        if (roles.contains("super_admin")) {
            return new AdminActorScope(true, true, Set.of());
        }

        if (roles.contains("platform_staff")) {
            PlatformAccessLookupResponse platformAccess = accessClient.getPlatformAccessByKeycloakUser(requireUserSub(userSub), internalAuth);
            Set<String> permissions = platformAccess.permissions() == null ? Set.of() : platformAccess.permissions();
            boolean inventoryManage = platformAccess.active() && permissions.contains(PLATFORM_INVENTORY_MANAGE);
            if (inventoryManage) {
                return new AdminActorScope(false, true, Set.of());
            }
        }

        Set<UUID> vendorIds = new LinkedHashSet<>();
        if (roles.contains("vendor_admin")) {
            List<VendorAccessMembershipResponse> memberships = vendorAccessClient.listAccessibleVendorsByKeycloakUser(requireUserSub(userSub), internalAuth);
            for (VendorAccessMembershipResponse membership : memberships) {
                if (membership != null && membership.vendorId() != null) {
                    vendorIds.add(membership.vendorId());
                }
            }
        }
        if (roles.contains("vendor_staff")) {
            List<VendorStaffAccessLookupResponse> vendorAccessRows = accessClient.listVendorStaffAccessByKeycloakUser(requireUserSub(userSub), internalAuth);
            for (VendorStaffAccessLookupResponse row : vendorAccessRows) {
                if (row == null || row.vendorId() == null || !row.active()) {
                    continue;
                }
                Set<String> perms = row.permissions() == null ? Set.of() : row.permissions();
                if (perms.contains(VENDOR_INVENTORY_MANAGE)) {
                    vendorIds.add(row.vendorId());
                }
            }
        }

        if (!vendorIds.isEmpty()) {
            return new AdminActorScope(false, false, Set.copyOf(vendorIds));
        }
        throw new UnauthorizedException("Caller does not have inventory admin access");
    }

    private String requireUserSub(String userSub) {
        if (!StringUtils.hasText(userSub)) {
            throw new UnauthorizedException("Missing authenticated user subject");
        }
        return userSub.trim();
    }

    private Set<String> parseRoles(String rolesHeader) {
        if (!StringUtils.hasText(rolesHeader)) {
            return Set.of();
        }
        Set<String> roles = new LinkedHashSet<>();
        for (String role : rolesHeader.split(",")) {
            if (role != null && !role.isBlank()) {
                roles.add(role.trim().toLowerCase(Locale.ROOT));
            }
        }
        return Set.copyOf(roles);
    }

    private UUID resolveVendorIdForVendorScopedActor(Set<UUID> vendorIds, UUID requestedVendorId) {
        if (vendorIds.isEmpty()) {
            throw new UnauthorizedException("No vendor inventory access found");
        }
        if (requestedVendorId != null) {
            if (!vendorIds.contains(requestedVendorId)) {
                throw new UnauthorizedException("Vendor-scoped user cannot use another vendorId");
            }
            return requestedVendorId;
        }
        if (vendorIds.size() == 1) {
            return vendorIds.iterator().next();
        }
        throw new ValidationException("vendorId is required when user has access to multiple vendors");
    }

    public record AdminActorScope(
            boolean superAdmin,
            boolean platformInventoryManage,
            Set<UUID> vendorInventoryVendorIds
    ) {
        public boolean isPlatformPrivileged() {
            return superAdmin || platformInventoryManage;
        }
    }
}
