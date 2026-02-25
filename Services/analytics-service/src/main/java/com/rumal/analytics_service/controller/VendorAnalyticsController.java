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
            @RequestHeader(value = "X-Vendor-Id", required = false) String vendorIdHeader,
            @PathVariable UUID vendorId) {
        verifyVendorAccess(internalAuth, userRoles, vendorIdHeader, vendorId);
        return vendorAnalyticsService.getVendorDashboard(vendorId);
    }

    private void verifyVendorAccess(String internalAuth, String userRoles, String vendorIdHeader, UUID requestedVendorId) {
        internalRequestVerifier.verify(internalAuth);
        if (userRoles == null || userRoles.isBlank()) {
            throw new UnauthorizedException("Vendor role required");
        }
        boolean isAdmin = false;
        boolean hasVendorRole = false;
        for (String role : userRoles.split(",")) {
            String trimmed = role.trim().toLowerCase();
            if (trimmed.equals("super_admin") || trimmed.equals("platform_staff")) {
                isAdmin = true;
                hasVendorRole = true;
                break;
            }
            if (VENDOR_ROLES.contains(trimmed)) {
                hasVendorRole = true;
            }
        }
        if (!hasVendorRole) {
            throw new UnauthorizedException("Vendor role required");
        }
        // Non-admin vendors can only view their own analytics
        if (!isAdmin) {
            if (vendorIdHeader == null || vendorIdHeader.isBlank()) {
                throw new UnauthorizedException("Vendor identification required");
            }
            try {
                UUID authenticatedVendorId = UUID.fromString(vendorIdHeader.trim());
                if (!authenticatedVendorId.equals(requestedVendorId)) {
                    throw new UnauthorizedException("You can only view your own analytics");
                }
            } catch (IllegalArgumentException e) {
                throw new UnauthorizedException("Invalid vendor identification");
            }
        }
    }
}
