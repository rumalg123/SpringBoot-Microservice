package com.rumal.analytics_service.controller;

import com.rumal.analytics_service.client.AccessScopeClient;
import com.rumal.analytics_service.client.CustomerAnalyticsClient;
import com.rumal.analytics_service.dto.CustomerInsightsResponse;
import com.rumal.analytics_service.exception.DownstreamHttpException;
import com.rumal.analytics_service.exception.UnauthorizedException;
import com.rumal.analytics_service.security.InternalRequestVerifier;
import com.rumal.analytics_service.service.CustomerAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/analytics/customer")
@RequiredArgsConstructor
public class CustomerAnalyticsController {

    private static final String PLATFORM_CUSTOMERS_READ = "platform.customers.read";

    private final InternalRequestVerifier internalRequestVerifier;
    private final CustomerAnalyticsService customerAnalyticsService;
    private final CustomerAnalyticsClient customerAnalyticsClient;
    private final AccessScopeClient accessScopeClient;

    @GetMapping("/{customerId}/insights")
    public CustomerInsightsResponse customerInsights(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String emailVerified,
            @PathVariable UUID customerId) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(emailVerified);
        verifyCustomerAccess(userSub, userRoles, customerId);
        return customerAnalyticsService.getCustomerInsights(customerId);
    }

    private void verifyCustomerAccess(String userSub, String userRoles, UUID requestedCustomerId) {
        Set<String> roles = parseRoles(userRoles);
        if (roles.contains("super_admin")) {
            return;
        }

        if (roles.contains("platform_staff")) {
            String normalizedUserSub = normalizeUserSub(userSub);
            var platformAccess = accessScopeClient.getPlatformAccessByKeycloakUser(normalizedUserSub);
            Set<String> permissions = platformAccess.permissions() == null ? Set.of() : platformAccess.permissions();
            if (platformAccess.active() && permissions.contains(PLATFORM_CUSTOMERS_READ)) {
                return;
            }
            throw new UnauthorizedException("platform_staff does not have customer analytics read permission");
        }

        String normalizedUserSub = normalizeUserSub(userSub);
        var customer = fetchCustomerByUserSub(normalizedUserSub);
        if (customer == null || customer.id() == null) {
            throw new UnauthorizedException("Customer profile not found for user");
        }
        if (!customer.id().equals(requestedCustomerId)) {
            throw new UnauthorizedException("You can only view your own analytics");
        }
    }

    private String normalizeUserSub(String userSub) {
        if (userSub == null || userSub.isBlank()) {
            throw new UnauthorizedException("User identification required");
        }
        return userSub.trim();
    }

    private void verifyEmailVerified(String emailVerified) {
        if (!StringUtils.hasText(emailVerified) || !"true".equalsIgnoreCase(emailVerified.trim())) {
            throw new UnauthorizedException("Email is not verified");
        }
    }

    private com.rumal.analytics_service.client.dto.InternalCustomerLookup fetchCustomerByUserSub(String userSub) {
        try {
            return customerAnalyticsClient.getCustomerByKeycloakId(userSub);
        } catch (DownstreamHttpException ex) {
            if (ex.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                throw new UnauthorizedException("Customer profile not found for user");
            }
            throw ex;
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
