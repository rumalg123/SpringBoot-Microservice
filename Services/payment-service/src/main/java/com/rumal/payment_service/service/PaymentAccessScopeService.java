package com.rumal.payment_service.service;

import com.rumal.payment_service.client.AccessClient;
import com.rumal.payment_service.client.VendorAccessClient;
import com.rumal.payment_service.dto.PlatformAccessLookupResponse;
import com.rumal.payment_service.dto.VendorAccessMembershipResponse;
import com.rumal.payment_service.dto.VendorStaffAccessLookupResponse;
import com.rumal.payment_service.exception.UnauthorizedException;
import com.rumal.payment_service.exception.ValidationException;
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
public class PaymentAccessScopeService {

    public static final String PLATFORM_PAYMENTS_READ = "platform.payments.read";
    public static final String PLATFORM_PAYMENTS_MANAGE = "platform.payments.manage";
    public static final String VENDOR_FINANCE_READ = "vendor.finance.read";
    public static final String VENDOR_FINANCE_MANAGE = "vendor.finance.manage";

    private final AccessClient accessClient;
    private final VendorAccessClient vendorAccessClient;

    public PaymentActorScope resolveScope(String userSub, String userRolesHeader, String internalAuth) {
        Set<String> roles = parseRoles(userRolesHeader);
        if (roles.contains("super_admin")) {
            return new PaymentActorScope(true, true, true, Set.of(), Set.of());
        }

        boolean platformRead = false;
        boolean platformManage = false;
        if (roles.contains("platform_staff")) {
            PlatformAccessLookupResponse platformAccess = accessClient.getPlatformAccessByKeycloakUser(requireUserSub(userSub), internalAuth);
            Set<String> permissions = platformAccess.permissions() == null ? Set.of() : platformAccess.permissions();
            if (platformAccess.active()) {
                platformManage = permissions.contains(PLATFORM_PAYMENTS_MANAGE);
                platformRead = platformManage || permissions.contains(PLATFORM_PAYMENTS_READ);
            }
        }

        Set<UUID> vendorFinanceReadVendorIds = new LinkedHashSet<>();
        Set<UUID> vendorFinanceManageVendorIds = new LinkedHashSet<>();
        if (roles.contains("vendor_admin")) {
            List<VendorAccessMembershipResponse> memberships = vendorAccessClient.listAccessibleVendorsByKeycloakUser(requireUserSub(userSub), internalAuth);
            for (VendorAccessMembershipResponse membership : memberships) {
                if (membership != null && membership.vendorId() != null) {
                    vendorFinanceReadVendorIds.add(membership.vendorId());
                    vendorFinanceManageVendorIds.add(membership.vendorId());
                }
            }
        }
        if (roles.contains("vendor_staff")) {
            List<VendorStaffAccessLookupResponse> accessRows = accessClient.listVendorStaffAccessByKeycloakUser(requireUserSub(userSub), internalAuth);
            for (VendorStaffAccessLookupResponse row : accessRows) {
                if (row == null || row.vendorId() == null || !row.active()) {
                    continue;
                }
                Set<String> permissions = normalizePermissionCodes(row.permissions());
                boolean canManageFinance = permissions.contains(VENDOR_FINANCE_MANAGE);
                boolean canReadFinance = canManageFinance || permissions.contains(VENDOR_FINANCE_READ);
                if (canReadFinance) {
                    vendorFinanceReadVendorIds.add(row.vendorId());
                }
                if (canManageFinance) {
                    vendorFinanceManageVendorIds.add(row.vendorId());
                }
            }
        }

        if (platformRead || platformManage || !vendorFinanceReadVendorIds.isEmpty() || !vendorFinanceManageVendorIds.isEmpty()) {
            return new PaymentActorScope(
                    false,
                    platformRead,
                    platformManage,
                    Set.copyOf(vendorFinanceReadVendorIds),
                    Set.copyOf(vendorFinanceManageVendorIds)
            );
        }
        throw new UnauthorizedException("Caller does not have payment access");
    }

    public void assertCanReadAdminPayments(PaymentActorScope scope) {
        if (!scope.isPlatformReadPrivileged()) {
            throw new UnauthorizedException("Caller does not have admin payment read access");
        }
    }

    public void assertCanManageAdminPayments(PaymentActorScope scope) {
        if (!scope.isPlatformManagePrivileged()) {
            throw new UnauthorizedException("Caller does not have admin payment management access");
        }
    }

    public UUID resolveVendorIdForVendorFinanceRead(PaymentActorScope scope, UUID requestedVendorId) {
        return resolveVendorScopedId(scope.vendorFinanceReadVendorIds(), requestedVendorId, "No vendor finance read access found");
    }

    public UUID resolveVendorIdForVendorFinanceManage(PaymentActorScope scope, UUID requestedVendorId) {
        return resolveVendorScopedId(scope.vendorFinanceManageVendorIds(), requestedVendorId, "No vendor finance management access found");
    }

    public UUID resolveVendorIdForVendorFinance(PaymentActorScope scope, UUID requestedVendorId) {
        return resolveVendorIdForVendorFinanceRead(scope, requestedVendorId);
    }

    private UUID resolveVendorScopedId(Set<UUID> allowedVendorIds, UUID requestedVendorId, String missingAccessMessage) {
        if (allowedVendorIds.isEmpty()) {
            throw new UnauthorizedException(missingAccessMessage);
        }
        if (requestedVendorId != null) {
            if (!allowedVendorIds.contains(requestedVendorId)) {
                throw new UnauthorizedException("Vendor-scoped user cannot use another vendorId");
            }
            return requestedVendorId;
        }
        if (allowedVendorIds.size() == 1) {
            return allowedVendorIds.iterator().next();
        }
        throw new ValidationException("vendorId is required when user has finance access to multiple vendors");
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
            String normalized = normalizeRole(role);
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

    private Set<String> normalizePermissionCodes(Set<String> rawPermissions) {
        if (rawPermissions == null || rawPermissions.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String permission : rawPermissions) {
            String code = normalizePermissionCode(permission);
            if (!code.isEmpty()) {
                normalized.add(code);
            }
        }
        return Set.copyOf(normalized);
    }

    private String normalizePermissionCode(String permission) {
        if (!StringUtils.hasText(permission)) {
            return "";
        }
        String normalized = permission.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "";
        }
        if (normalized.contains(".")) {
            return normalized;
        }
        return switch (normalized) {
            case "finance_read" -> VENDOR_FINANCE_READ;
            case "finance_manage" -> VENDOR_FINANCE_MANAGE;
            default -> normalized;
        };
    }

    public record PaymentActorScope(
            boolean superAdmin,
            boolean platformPaymentsRead,
            boolean platformPaymentsManage,
            Set<UUID> vendorFinanceReadVendorIds,
            Set<UUID> vendorFinanceManageVendorIds
    ) {
        public boolean isPlatformReadPrivileged() {
            return superAdmin || platformPaymentsRead || platformPaymentsManage;
        }

        public boolean isPlatformManagePrivileged() {
            return superAdmin || platformPaymentsManage;
        }
    }
}
