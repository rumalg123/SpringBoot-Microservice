package com.rumal.analytics_service.controller;

import com.rumal.analytics_service.dto.*;
import com.rumal.analytics_service.exception.UnauthorizedException;
import com.rumal.analytics_service.security.InternalRequestVerifier;
import com.rumal.analytics_service.service.AdminAnalyticsService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Validated
@RestController
@RequestMapping("/analytics/admin")
@RequiredArgsConstructor
public class AdminAnalyticsController {

    private static final Set<String> ADMIN_ROLES = Set.of("super_admin", "platform_admin", "platform_staff");
    private static final Set<String> ALLOWED_SORT_BY = Set.of(
        "ORDERS_COMPLETED", "AVERAGE_RATING", "FULFILLMENT_RATE", "DISPUTE_RATE", "REVENUE"
    );

    private final InternalRequestVerifier internalRequestVerifier;
    private final AdminAnalyticsService adminAnalyticsService;

    @GetMapping("/dashboard")
    public AdminDashboardAnalytics dashboard(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int periodDays) {
        verifyAdminAccess(internalAuth, userRoles);
        return adminAnalyticsService.getDashboardSummary(periodDays);
    }

    @GetMapping("/revenue-trend")
    public AdminRevenueTrendResponse revenueTrend(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {
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
        String normalizedSortBy = sortBy.trim().toUpperCase();
        if (!ALLOWED_SORT_BY.contains(normalizedSortBy)) {
            normalizedSortBy = "ORDERS_COMPLETED";
        }
        return adminAnalyticsService.getVendorLeaderboard(normalizedSortBy);
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
        Set<String> roles = parseRoles(userRoles);
        if (roles.isEmpty()) {
            throw new UnauthorizedException("Admin role required");
        }
        boolean hasAdminRole = roles.stream().anyMatch(ADMIN_ROLES::contains);
        if (!hasAdminRole) {
            throw new UnauthorizedException("Admin role required");
        }
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
