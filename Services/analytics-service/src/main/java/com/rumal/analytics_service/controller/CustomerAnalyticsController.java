package com.rumal.analytics_service.controller;

import com.rumal.analytics_service.dto.CustomerInsightsResponse;
import com.rumal.analytics_service.exception.UnauthorizedException;
import com.rumal.analytics_service.security.InternalRequestVerifier;
import com.rumal.analytics_service.service.CustomerAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/analytics/customer")
@RequiredArgsConstructor
public class CustomerAnalyticsController {

    private static final Set<String> ADMIN_ROLES = Set.of("super_admin", "platform_staff");

    private final InternalRequestVerifier internalRequestVerifier;
    private final CustomerAnalyticsService customerAnalyticsService;

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
        if (userRoles != null && !userRoles.isBlank()) {
            for (String role : userRoles.split(",")) {
                if (ADMIN_ROLES.contains(role.trim().toLowerCase())) {
                    return;
                }
            }
        }
        if (userSub == null || userSub.isBlank()) {
            throw new UnauthorizedException("User identification required");
        }
        try {
            UUID authenticatedUserId = UUID.fromString(userSub.trim());
            if (!authenticatedUserId.equals(requestedCustomerId)) {
                throw new UnauthorizedException("You can only view your own analytics");
            }
        } catch (IllegalArgumentException e) {
            throw new UnauthorizedException("Invalid user identification");
        }
    }
}
