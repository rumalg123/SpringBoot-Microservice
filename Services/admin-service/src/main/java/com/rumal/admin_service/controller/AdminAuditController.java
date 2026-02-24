package com.rumal.admin_service.controller;

import com.rumal.admin_service.dto.AdminAuditLogResponse;
import com.rumal.admin_service.dto.PageResponse;
import com.rumal.admin_service.security.InternalRequestVerifier;
import com.rumal.admin_service.service.AdminActorScopeService;
import com.rumal.admin_service.service.AdminAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/admin/audit-log")
@RequiredArgsConstructor
public class AdminAuditController {

    private final AdminAuditService auditService;
    private final InternalRequestVerifier internalRequestVerifier;
    private final AdminActorScopeService adminActorScopeService;

    @GetMapping
    public PageResponse<AdminAuditLogResponse> listAuditLogs(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(required = false) String actorKeycloakId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        internalRequestVerifier.verify(internalAuth);
        if (!adminActorScopeService.hasRole(userRoles, "super_admin")) {
            throw new com.rumal.admin_service.exception.UnauthorizedException("Only super_admin can view audit logs");
        }
        return auditService.listAuditLogs(actorKeycloakId, action, resourceType, resourceId, from, to, page, size);
    }
}
