package com.rumal.admin_service.controller;

import com.rumal.admin_service.security.InternalRequestVerifier;
import com.rumal.admin_service.service.AdminAccessService;
import com.rumal.admin_service.service.AdminActorScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin/platform-staff")
@RequiredArgsConstructor
public class AdminPlatformStaffController {

    private final AdminAccessService adminAccessService;
    private final InternalRequestVerifier internalRequestVerifier;
    private final AdminActorScopeService adminActorScopeService;

    @GetMapping
    public List<Map<String, Object>> listAll(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminActorScopeService.assertHasRole(userSub, userRoles, "super_admin");
        return adminAccessService.listPlatformStaff(internalAuth);
    }

    @GetMapping("/deleted")
    public List<Map<String, Object>> listDeleted(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminActorScopeService.assertHasRole(userSub, userRoles, "super_admin");
        return adminAccessService.listDeletedPlatformStaff(internalAuth);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "X-Action-Reason", required = false) String actionReason,
            @RequestBody @NotNull Map<String, Object> request
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminActorScopeService.assertHasRole(userSub, userRoles, "super_admin");
        return adminAccessService.createPlatformStaff(request, internalAuth, userSub, userRoles, actionReason);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "X-Action-Reason", required = false) String actionReason,
            @PathVariable UUID id,
            @RequestBody @NotNull Map<String, Object> request
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminActorScopeService.assertHasRole(userSub, userRoles, "super_admin");
        return adminAccessService.updatePlatformStaff(id, request, internalAuth, userSub, userRoles, actionReason);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "X-Action-Reason", required = false) String actionReason,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminActorScopeService.assertHasRole(userSub, userRoles, "super_admin");
        adminAccessService.deletePlatformStaff(id, internalAuth, userSub, userRoles, actionReason);
    }

    @PostMapping("/{id}/restore")
    public Map<String, Object> restore(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "X-Action-Reason", required = false) String actionReason,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminActorScopeService.assertHasRole(userSub, userRoles, "super_admin");
        return adminAccessService.restorePlatformStaff(id, internalAuth, userSub, userRoles, actionReason);
    }
}
