package com.rumal.analytics_service.controller;

import com.rumal.analytics_service.client.AccessScopeClient;
import com.rumal.analytics_service.dto.DashboardSummaryResponse;
import com.rumal.analytics_service.exception.UnauthorizedException;
import com.rumal.analytics_service.security.InternalRequestVerifier;
import com.rumal.analytics_service.service.AdminAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class LegacyDashboardController {

    private static final String PLATFORM_ANALYTICS_READ = "platform.analytics.read";

    private final InternalRequestVerifier internalRequestVerifier;
    private final AdminAnalyticsService adminAnalyticsService;
    private final AccessScopeClient accessScopeClient;

    @GetMapping("/summary")
    public DashboardSummaryResponse summary(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles
    ) {
        verifyAdminAccess(internalAuth, userSub, userRoles);
        return adminAnalyticsService.getLegacyDashboardSummary();
    }

    private void verifyAdminAccess(String internalAuth, String userSub, String userRoles) {
        internalRequestVerifier.verify(internalAuth);
        Set<String> roles = parseRoles(userRoles);
        if (roles.contains("super_admin")) {
            return;
        }
        if (!roles.contains("platform_staff")) {
            throw new UnauthorizedException("Admin role required");
        }
        if (!StringUtils.hasText(userSub)) {
            throw new UnauthorizedException("User identification required");
        }

        var platformAccess = accessScopeClient.getPlatformAccessByKeycloakUser(userSub.trim());
        Set<String> permissions = platformAccess.permissions() == null ? Set.of() : platformAccess.permissions();
        if (!platformAccess.active() || !permissions.contains(PLATFORM_ANALYTICS_READ)) {
            throw new UnauthorizedException("platform_staff does not have analytics read permission");
        }
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
}
