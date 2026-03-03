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
    private static final Set<String> ADMIN_ROLES = Set.of("super_admin", "platform_admin", "platform_staff");

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

        boolean isPlatformAdminLike = roles.stream().anyMatch(ADMIN_ROLES::contains);
        if (isPlatformAdminLike) {
            return;
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
            if (!StringUtils.hasText(role)) {
                continue;
            }
            String normalized = role.trim()
                    .toLowerCase(Locale.ROOT)
                    .replace("role_", "")
                    .replace('-', '_')
                    .replace(' ', '_');
            if (!normalized.isEmpty()) {
                roles.add(normalized);
            }
        }
        return Set.copyOf(roles);
    }
}
