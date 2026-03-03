package com.rumal.analytics_service.controller;

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

    private static final Set<String> ADMIN_ROLES = Set.of("super_admin", "platform_admin", "platform_staff");

    private final InternalRequestVerifier internalRequestVerifier;
    private final CustomerAnalyticsService customerAnalyticsService;
    private final CustomerAnalyticsClient customerAnalyticsClient;

    @GetMapping("/{customerId}/insights")
    public CustomerInsightsResponse customerInsights(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID customerId) {
        internalRequestVerifier.verify(internalAuth);
        verifyCustomerAccess(userSub, userRoles, customerId);
        return customerAnalyticsService.getCustomerInsights(customerId);
    }

    private void verifyCustomerAccess(String userSub, String userRoles, UUID requestedCustomerId) {
        Set<String> roles = parseRoles(userRoles);
        if (roles.stream().anyMatch(ADMIN_ROLES::contains)) {
            return;
        }
        if (userSub == null || userSub.isBlank()) {
            throw new UnauthorizedException("User identification required");
        }
        var customer = fetchCustomerByUserSub(userSub.trim());
        if (customer == null || customer.id() == null) {
            throw new UnauthorizedException("Customer profile not found for user");
        }
        if (!customer.id().equals(requestedCustomerId)) {
            throw new UnauthorizedException("You can only view your own analytics");
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
