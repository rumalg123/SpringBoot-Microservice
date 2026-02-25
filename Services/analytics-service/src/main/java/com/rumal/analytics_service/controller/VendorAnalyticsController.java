package com.rumal.analytics_service.controller;

import com.rumal.analytics_service.dto.VendorDashboardAnalytics;
import com.rumal.analytics_service.exception.UnauthorizedException;
import com.rumal.analytics_service.security.InternalRequestVerifier;
import com.rumal.analytics_service.service.VendorAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/analytics/vendor")
@RequiredArgsConstructor
public class VendorAnalyticsController {

    private static final Set<String> VENDOR_ROLES = Set.of("vendor_admin", "vendor_staff");

    private final InternalRequestVerifier internalRequestVerifier;
    private final VendorAnalyticsService vendorAnalyticsService;

    @GetMapping("/{vendorId}/dashboard")
    public VendorDashboardAnalytics vendorDashboard(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID vendorId) {
        verifyVendorAccess(internalAuth, userRoles);
        return vendorAnalyticsService.getVendorDashboard(vendorId);
    }

    private void verifyVendorAccess(String internalAuth, String userRoles) {
        internalRequestVerifier.verify(internalAuth);
        if (userRoles == null || userRoles.isBlank()) {
            throw new UnauthorizedException("Vendor role required");
        }
        boolean hasVendorRole = false;
        for (String role : userRoles.split(",")) {
            String trimmed = role.trim().toLowerCase();
            if (VENDOR_ROLES.contains(trimmed) || trimmed.equals("super_admin") || trimmed.equals("platform_staff")) {
                hasVendorRole = true;
                break;
            }
        }
        if (!hasVendorRole) {
            throw new UnauthorizedException("Vendor role required");
        }
    }
}
