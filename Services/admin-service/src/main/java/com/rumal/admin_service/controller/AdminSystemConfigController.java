package com.rumal.admin_service.controller;

import com.rumal.admin_service.dto.FeatureFlagResponse;
import com.rumal.admin_service.dto.SystemConfigResponse;
import com.rumal.admin_service.dto.UpsertFeatureFlagRequest;
import com.rumal.admin_service.dto.UpsertSystemConfigRequest;
import com.rumal.admin_service.security.InternalRequestVerifier;
import com.rumal.admin_service.service.AdminActorScopeService;
import com.rumal.admin_service.service.AdminAuditService;
import com.rumal.admin_service.service.FeatureFlagService;
import com.rumal.admin_service.service.SystemConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/system")
@RequiredArgsConstructor
public class AdminSystemConfigController {

    private final SystemConfigService systemConfigService;
    private final FeatureFlagService featureFlagService;
    private final InternalRequestVerifier internalRequestVerifier;
    private final AdminActorScopeService adminActorScopeService;
    private final AdminAuditService auditService;

    @GetMapping("/config")
    public List<SystemConfigResponse> listConfigs(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles
    ) {
        internalRequestVerifier.verify(internalAuth);
        assertSuperAdmin(userRoles);
        return systemConfigService.listAll();
    }

    @PutMapping("/config")
    public SystemConfigResponse upsertConfig(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @Valid @RequestBody UpsertSystemConfigRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        assertSuperAdmin(userRoles);
        SystemConfigResponse result = systemConfigService.upsert(request);
        auditService.log(userSub, userRoles, "UPSERT_SYSTEM_CONFIG", "SYSTEM_CONFIG", request.configKey(), null, null);
        return result;
    }

    @DeleteMapping("/config/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteConfig(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        assertSuperAdmin(userRoles);
        systemConfigService.delete(id);
        auditService.log(userSub, userRoles, "DELETE_SYSTEM_CONFIG", "SYSTEM_CONFIG", id.toString(), null, null);
    }

    @GetMapping("/feature-flags")
    public List<FeatureFlagResponse> listFeatureFlags(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles
    ) {
        internalRequestVerifier.verify(internalAuth);
        assertSuperAdmin(userRoles);
        return featureFlagService.listAll();
    }

    @PutMapping("/feature-flags")
    public FeatureFlagResponse upsertFeatureFlag(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @Valid @RequestBody UpsertFeatureFlagRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        assertSuperAdmin(userRoles);
        FeatureFlagResponse result = featureFlagService.upsert(request);
        auditService.log(userSub, userRoles, "UPSERT_FEATURE_FLAG", "FEATURE_FLAG", request.flagKey(), null, null);
        return result;
    }

    @DeleteMapping("/feature-flags/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFeatureFlag(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        assertSuperAdmin(userRoles);
        featureFlagService.delete(id);
        auditService.log(userSub, userRoles, "DELETE_FEATURE_FLAG", "FEATURE_FLAG", id.toString(), null, null);
    }

    private void assertSuperAdmin(String userRoles) {
        if (!adminActorScopeService.hasRole(userRoles, "super_admin")) {
            throw new com.rumal.admin_service.exception.UnauthorizedException("Only super_admin can manage system configuration");
        }
    }
}
