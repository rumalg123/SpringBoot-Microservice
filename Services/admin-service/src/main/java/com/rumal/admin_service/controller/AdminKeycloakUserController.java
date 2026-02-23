package com.rumal.admin_service.controller;

import com.rumal.admin_service.auth.KeycloakVendorAdminManagementService;
import com.rumal.admin_service.security.InternalRequestVerifier;
import com.rumal.admin_service.service.AdminActorScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/keycloak/users")
@RequiredArgsConstructor
public class AdminKeycloakUserController {

    private final KeycloakVendorAdminManagementService keycloakVendorAdminManagementService;
    private final AdminActorScopeService adminActorScopeService;
    private final InternalRequestVerifier internalRequestVerifier;

    @GetMapping("/search")
    public List<KeycloakVendorAdminManagementService.KeycloakUserSearchResult> search(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam("q") String query,
            @RequestParam(value = "limit", defaultValue = "8") int limit
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminActorScopeService.assertCanSearchKeycloakUsers(userRoles);
        return keycloakVendorAdminManagementService.searchUsers(query, limit);
    }
}
