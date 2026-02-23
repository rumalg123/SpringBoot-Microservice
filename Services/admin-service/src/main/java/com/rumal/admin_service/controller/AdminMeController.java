package com.rumal.admin_service.controller;

import com.rumal.admin_service.dto.AdminCapabilitiesResponse;
import com.rumal.admin_service.security.InternalRequestVerifier;
import com.rumal.admin_service.service.AdminActorScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/me")
@RequiredArgsConstructor
public class AdminMeController {

    private final InternalRequestVerifier internalRequestVerifier;
    private final AdminActorScopeService adminActorScopeService;

    @GetMapping("/capabilities")
    public AdminCapabilitiesResponse getCapabilities(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles
    ) {
        internalRequestVerifier.verify(internalAuth);
        return adminActorScopeService.describeCapabilities(userSub, userRoles, internalAuth);
    }
}
