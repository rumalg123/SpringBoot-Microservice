package com.rumal.admin_service.controller;

import com.rumal.admin_service.security.InternalRequestVerifier;
import com.rumal.admin_service.service.AdminActorScopeService;
import com.rumal.admin_service.service.AdminAuditService;
import com.rumal.admin_service.service.AdminPosterService;
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
@RequestMapping("/admin/posters")
@RequiredArgsConstructor
public class AdminPosterController {

    private final AdminPosterService adminPosterService;
    private final AdminActorScopeService adminActorScopeService;
    private final InternalRequestVerifier internalRequestVerifier;
    private final AdminAuditService auditService;

    @GetMapping
    public List<Map<String, Object>> listAll(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminActorScopeService.assertCanManagePosters(userSub, userRoles, internalAuth);
        return adminPosterService.listAll(internalAuth, userSub, userRoles);
    }

    @GetMapping("/deleted")
    public List<Map<String, Object>> listDeleted(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminActorScopeService.assertCanManagePosters(userSub, userRoles, internalAuth);
        return adminPosterService.listDeleted(internalAuth, userSub, userRoles);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestBody @NotNull Map<String, Object> request
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminActorScopeService.assertCanManagePosters(userSub, userRoles, internalAuth);
        Map<String, Object> result = adminPosterService.create(request, internalAuth, userSub, userRoles);
        auditService.log(userSub, userRoles, "CREATE_POSTER", "POSTER", String.valueOf(result.get("id")), null, null);
        return result;
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID id,
            @RequestBody @NotNull Map<String, Object> request
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminActorScopeService.assertCanManagePosters(userSub, userRoles, internalAuth);
        Map<String, Object> result = adminPosterService.update(id, request, internalAuth, userSub, userRoles);
        auditService.log(userSub, userRoles, "UPDATE_POSTER", "POSTER", id.toString(), null, null);
        return result;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminActorScopeService.assertCanManagePosters(userSub, userRoles, internalAuth);
        adminPosterService.delete(id, internalAuth, userSub, userRoles);
        auditService.log(userSub, userRoles, "DELETE_POSTER", "POSTER", id.toString(), null, null);
    }

    @PostMapping("/{id}/restore")
    public Map<String, Object> restore(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminActorScopeService.assertCanManagePosters(userSub, userRoles, internalAuth);
        Map<String, Object> result = adminPosterService.restore(id, internalAuth, userSub, userRoles);
        auditService.log(userSub, userRoles, "RESTORE_POSTER", "POSTER", id.toString(), null, null);
        return result;
    }

    @PostMapping("/images/presign")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> prepareImageUploads(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestBody @NotNull Map<String, Object> request
    ) {
        internalRequestVerifier.verify(internalAuth);
        adminActorScopeService.assertCanManagePosters(userSub, userRoles, internalAuth);
        return adminPosterService.prepareImageUploads(request, internalAuth, userSub, userRoles);
    }
}
