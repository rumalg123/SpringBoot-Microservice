package com.rumal.analytics_service.controller;

import com.rumal.analytics_service.client.AccessScopeClient;
import com.rumal.analytics_service.dto.VendorDashboardAnalytics;
import com.rumal.analytics_service.exception.UnauthorizedException;
import com.rumal.analytics_service.security.InternalRequestVerifier;
import com.rumal.analytics_service.service.VendorAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/analytics/vendor")
@RequiredArgsConstructor
public class VendorAnalyticsController {

    private static final Set<String> VENDOR_ROLES = Set.of("vendor_admin", "vendor_staff");
    private static final String PLATFORM_ANALYTICS_READ = "platform.analytics.read";

    private final InternalRequestVerifier internalRequestVerifier;
    private final VendorAnalyticsService vendorAnalyticsService;
    private final AccessScopeClient accessScopeClient;

    @GetMapping("/{vendorId}/dashboard")
    public VendorDashboardAnalytics vendorDashboard(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID vendorId) {
        verifyVendorAccess(internalAuth, userSub, userRoles, vendorId);
        return vendorAnalyticsService.getVendorDashboard(vendorId);
    }

    private void verifyVendorAccess(String internalAuth, String userSub, String userRoles, UUID requestedVendorId) {
        internalRequestVerifier.verify(internalAuth);
        Set<String> roles = parseRoles(userRoles);
        if (roles.isEmpty()) {
            throw new UnauthorizedException("Vendor role required");
        }

        if (roles.contains("super_admin")) {
            return;
        }

        if (roles.contains("platform_staff")) {
            String normalizedUserSub = normalizeUserSub(userSub);
            var platformAccess = accessScopeClient.getPlatformAccessByKeycloakUser(normalizedUserSub);
            Set<String> permissions = platformAccess.permissions() == null ? Set.of() : platformAccess.permissions();
            if (platformAccess.active() && permissions.contains(PLATFORM_ANALYTICS_READ)) {
                return;
            }
            throw new UnauthorizedException("platform_staff does not have analytics read permission");
        }

        boolean hasVendorRole = roles.stream().anyMatch(VENDOR_ROLES::contains);
        if (!hasVendorRole) {
            throw new UnauthorizedException("Vendor role required");
        }

        String normalizedUserSub = normalizeUserSub(userSub);
        Set<UUID> allowedVendorIds = new LinkedHashSet<>();

        if (roles.contains("vendor_admin")) {
            allowedVendorIds.addAll(accessScopeClient.listVendorMembershipVendorIds(normalizedUserSub));
        }
        if (roles.contains("vendor_staff")) {
            allowedVendorIds.addAll(accessScopeClient.listVendorStaffAnalyticsVendorIds(normalizedUserSub));
        }

        if (allowedVendorIds.isEmpty()) {
            throw new UnauthorizedException("Vendor analytics access is not configured for this user");
        }
        if (!allowedVendorIds.contains(requestedVendorId)) {
            throw new UnauthorizedException("You can only view your own analytics");
        }
    }

    private String normalizeUserSub(String userSub) {
        if (!StringUtils.hasText(userSub)) {
            throw new UnauthorizedException("User identification required");
        }
        return userSub.trim();
    }

    private Set<String> parseRoles(String userRoles) {
        if (!StringUtils.hasText(userRoles)) {
            return Set.of();
        }
        Set<String> roles = new LinkedHashSet<>();
        for (String role : userRoles.split(",")) {
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
}
