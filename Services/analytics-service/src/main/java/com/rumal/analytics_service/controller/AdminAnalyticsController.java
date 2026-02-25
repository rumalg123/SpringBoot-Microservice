package com.rumal.analytics_service.controller;

import com.rumal.analytics_service.dto.*;
import com.rumal.analytics_service.exception.UnauthorizedException;
import com.rumal.analytics_service.security.InternalRequestVerifier;
import com.rumal.analytics_service.service.AdminAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/analytics/admin")
@RequiredArgsConstructor
public class AdminAnalyticsController {

    private static final Set<String> ADMIN_ROLES = Set.of("super_admin", "platform_staff");

    private final InternalRequestVerifier internalRequestVerifier;
    private final AdminAnalyticsService adminAnalyticsService;

    @GetMapping("/dashboard")
    public AdminDashboardAnalytics dashboard(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(defaultValue = "30") int periodDays) {
        verifyAdminAccess(internalAuth, userRoles);
        return adminAnalyticsService.getDashboardSummary(periodDays);
    }

    @GetMapping("/revenue-trend")
    public AdminRevenueTrendResponse revenueTrend(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(defaultValue = "30") int days) {
        verifyAdminAccess(internalAuth, userRoles);
        return adminAnalyticsService.getRevenueTrend(days);
    }

    @GetMapping("/top-products")
    public AdminTopProductsResponse topProducts(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles) {
        verifyAdminAccess(internalAuth, userRoles);
        return adminAnalyticsService.getTopProducts();
    }

    @GetMapping("/customer-segmentation")
    public AdminCustomerSegmentationResponse customerSegmentation(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles) {
        verifyAdminAccess(internalAuth, userRoles);
        return adminAnalyticsService.getCustomerSegmentation();
    }

    @GetMapping("/vendor-leaderboard")
    public AdminVendorLeaderboardResponse vendorLeaderboard(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(defaultValue = "ORDERS_COMPLETED") String sortBy) {
        verifyAdminAccess(internalAuth, userRoles);
        return adminAnalyticsService.getVendorLeaderboard(sortBy);
    }

    @GetMapping("/inventory-health")
    public AdminInventoryHealthResponse inventoryHealth(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles) {
        verifyAdminAccess(internalAuth, userRoles);
        return adminAnalyticsService.getInventoryHealth();
    }

    @GetMapping("/promotion-roi")
    public AdminPromotionRoiResponse promotionRoi(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles) {
        verifyAdminAccess(internalAuth, userRoles);
        return adminAnalyticsService.getPromotionRoi();
    }

    @GetMapping("/review-analytics")
    public AdminReviewAnalyticsResponse reviewAnalytics(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles) {
        verifyAdminAccess(internalAuth, userRoles);
        return adminAnalyticsService.getReviewAnalytics();
    }

    @PostMapping("/cache/evict")
    public ResponseEntity<Void> evictCaches(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles) {
        verifyAdminAccess(internalAuth, userRoles);
        adminAnalyticsService.evictAllCaches();
        return ResponseEntity.noContent().build();
    }

    private void verifyAdminAccess(String internalAuth, String userRoles) {
        internalRequestVerifier.verify(internalAuth);
        if (userRoles == null || userRoles.isBlank()) {
            throw new UnauthorizedException("Admin role required");
        }
        boolean hasAdminRole = false;
        for (String role : userRoles.split(",")) {
            if (ADMIN_ROLES.contains(role.trim().toLowerCase())) {
                hasAdminRole = true;
                break;
            }
        }
        if (!hasAdminRole) {
            throw new UnauthorizedException("Admin role required");
        }
    }
}
