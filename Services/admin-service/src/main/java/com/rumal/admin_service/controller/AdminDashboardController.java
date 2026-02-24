package com.rumal.admin_service.controller;

import com.rumal.admin_service.dto.DashboardSummaryResponse;
import com.rumal.admin_service.security.InternalRequestVerifier;
import com.rumal.admin_service.service.AdminActorScopeService;
import com.rumal.admin_service.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final DashboardService dashboardService;
    private final InternalRequestVerifier internalRequestVerifier;
    private final AdminActorScopeService adminActorScopeService;

    @GetMapping("/summary")
    public DashboardSummaryResponse getSummary(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles
    ) {
        internalRequestVerifier.verify(internalAuth);
        if (!adminActorScopeService.hasRole(userRoles, "super_admin")
                && !adminActorScopeService.hasRole(userRoles, "platform_staff")) {
            throw new com.rumal.admin_service.exception.UnauthorizedException("Insufficient permissions for dashboard access");
        }
        return dashboardService.getSummary(internalAuth);
    }
}
