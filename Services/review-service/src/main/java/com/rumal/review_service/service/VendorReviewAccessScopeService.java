package com.rumal.review_service.service;

import com.rumal.review_service.client.AccessClient;
import com.rumal.review_service.client.VendorClient;
import com.rumal.review_service.dto.VendorStaffAccessLookupResponse;
import com.rumal.review_service.exception.UnauthorizedException;
import com.rumal.review_service.exception.ValidationException;
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
public class VendorReviewAccessScopeService {

    private static final String VENDOR_REVIEWS_MANAGE = "vendor.reviews.manage";
    private static final String LEGACY_REVIEWS_MANAGE = "reviews_manage";

    private final VendorClient vendorClient;
    private final AccessClient accessClient;

    public UUID resolveVendorIdForReviewManage(
            String userSub,
            String userRolesHeader,
            String internalAuth,
            UUID vendorIdHint
    ) {
        String normalizedSub = requireUserSub(userSub);
        Set<String> roles = parseRoles(userRolesHeader);

        if (roles.contains("super_admin") || roles.contains("vendor_admin")) {
            return vendorClient.getVendorIdByKeycloakSub(normalizedSub, vendorIdHint);
        }
        if (!roles.contains("vendor_staff")) {
            throw new UnauthorizedException("Vendor role required");
        }

        List<VendorStaffAccessLookupResponse> accessRows =
                accessClient.listVendorStaffAccessByKeycloakUser(normalizedSub, internalAuth);
        Set<UUID> allowedVendorIds = new LinkedHashSet<>();
        for (VendorStaffAccessLookupResponse row : accessRows) {
            if (row == null || row.vendorId() == null || !row.active()) {
                continue;
            }
            if (hasReviewManagePermission(row.permissions())) {
                allowedVendorIds.add(row.vendorId());
            }
        }
        return resolveVendorIdForVendorStaff(allowedVendorIds, vendorIdHint);
    }

    private UUID resolveVendorIdForVendorStaff(Set<UUID> allowedVendorIds, UUID vendorIdHint) {
        if (allowedVendorIds.isEmpty()) {
            throw new UnauthorizedException("vendor_staff does not have review management permission");
        }
        if (vendorIdHint != null) {
            if (!allowedVendorIds.contains(vendorIdHint)) {
                throw new UnauthorizedException("vendor_staff cannot access another vendor");
            }
            return vendorIdHint;
        }
        if (allowedVendorIds.size() == 1) {
            return allowedVendorIds.iterator().next();
        }
        throw new ValidationException("vendorId is required when user has access to multiple vendors");
    }

    private boolean hasReviewManagePermission(Set<String> rawPermissions) {
        if (rawPermissions == null || rawPermissions.isEmpty()) {
            return false;
        }
        for (String permission : rawPermissions) {
            String normalized = normalizePermissionCode(permission);
            if (VENDOR_REVIEWS_MANAGE.equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    private String normalizePermissionCode(String permission) {
        if (!StringUtils.hasText(permission)) {
            return "";
        }
        String normalized = permission.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains(".")) {
            return normalized;
        }
        if (LEGACY_REVIEWS_MANAGE.equals(normalized)) {
            return VENDOR_REVIEWS_MANAGE;
        }
        return normalized;
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

    private String requireUserSub(String userSub) {
        if (!StringUtils.hasText(userSub)) {
            throw new UnauthorizedException("Authentication required");
        }
        return userSub.trim();
    }
}
