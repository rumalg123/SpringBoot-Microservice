package com.rumal.admin_service.controller;

import com.rumal.admin_service.auth.KeycloakVendorAdminManagementService;
import com.rumal.admin_service.security.InternalRequestVerifier;
import com.rumal.admin_service.service.AdminActorScopeService;
import com.rumal.admin_service.service.AdminVendorService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/admin/keycloak/users")
@RequiredArgsConstructor
public class AdminKeycloakUserController {

    private final KeycloakVendorAdminManagementService keycloakVendorAdminManagementService;
    private final AdminActorScopeService adminActorScopeService;
    private final AdminVendorService adminVendorService;
    private final InternalRequestVerifier internalRequestVerifier;

    @GetMapping("/search")
    public List<KeycloakVendorAdminManagementService.KeycloakUserSearchResult> search(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam("q") String query,
            @RequestParam(value = "limit", defaultValue = "8") int limit
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminActorScopeService.assertCanSearchKeycloakUsers(userRoles);
        int safeLimit = Math.max(1, Math.min(limit, 20));

        boolean isSuperAdmin = adminActorScopeService.hasRole(userRoles, "super_admin");
        if (isSuperAdmin) {
            return keycloakVendorAdminManagementService.searchUsers(query, safeLimit);
        }

        boolean isVendorAdmin = adminActorScopeService.hasRole(userRoles, "vendor_admin");
        if (!isVendorAdmin) {
            return List.of();
        }

        Set<String> linkedKeycloakUserIds = adminVendorService.listLinkedKeycloakUserIdsForVendorAdmin(
                requireUserSub(userSub),
                internalAuth,
                userRoles
        );
        if (linkedKeycloakUserIds.isEmpty()) {
            return List.of();
        }

        int candidateFetchLimit = Math.max(50, safeLimit * 10);
        return keycloakVendorAdminManagementService.searchUsers(query, candidateFetchLimit).stream()
                .filter(user -> StringUtils.hasText(user.id()))
                .filter(user -> linkedKeycloakUserIds.contains(user.id().trim().toLowerCase(Locale.ROOT)))
                .limit(safeLimit)
                .toList();
    }

    private String requireUserSub(String userSub) {
        if (!StringUtils.hasText(userSub)) {
            throw new com.rumal.admin_service.exception.UnauthorizedException("Missing authenticated user subject");
        }
        return userSub.trim();
    }
}
