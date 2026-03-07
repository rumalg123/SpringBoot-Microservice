package com.rumal.access_service.controller;

import com.rumal.access_service.dto.PermissionGroupResponse;
import com.rumal.access_service.dto.UpsertPermissionGroupRequest;
import com.rumal.access_service.entity.PermissionGroupScope;
import com.rumal.access_service.exception.UnauthorizedException;
import com.rumal.access_service.security.InternalRequestVerifier;
import com.rumal.access_service.service.AccessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/admin/permission-groups")
@RequiredArgsConstructor
public class AdminPermissionGroupController {

    private static final String INTERNAL_HEADER = "X-Internal-Auth";
    private final InternalRequestVerifier internalRequestVerifier;
    private final AccessService accessService;

    @GetMapping
    public Page<PermissionGroupResponse> listAll(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(required = false) PermissionGroupScope scope,
            @PageableDefault(size = 20, sort = "name") Pageable pageable
    ) {
        internalRequestVerifier.verify(internalAuth);
        requirePlatformAdmin(userRoles);
        return accessService.listPermissionGroups(scope, pageable);
    }

    @GetMapping("/{id}")
    public PermissionGroupResponse getById(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        requirePlatformAdmin(userRoles);
        return accessService.getPermissionGroupById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PermissionGroupResponse create(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @Valid @RequestBody UpsertPermissionGroupRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        requirePlatformAdmin(userRoles);
        return accessService.createPermissionGroup(request);
    }

    @PutMapping("/{id}")
    public PermissionGroupResponse update(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID id,
            @Valid @RequestBody UpsertPermissionGroupRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        requirePlatformAdmin(userRoles);
        return accessService.updatePermissionGroup(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        requirePlatformAdmin(userRoles);
        accessService.deletePermissionGroup(id);
    }

    private void requirePlatformAdmin(String userRoles) {
        Set<String> roles = parseRoles(userRoles);
        if (roles.contains("super_admin")) {
            return;
        }
        throw new UnauthorizedException("Caller does not have platform admin access");
    }

    private Set<String> parseRoles(String userRoles) {
        if (userRoles == null || userRoles.isBlank()) {
            return Set.of();
        }
        Set<String> roles = new LinkedHashSet<>();
        for (String rawRole : userRoles.split(",")) {
            String normalized = normalizeRole(rawRole);
            if (!normalized.isEmpty()) {
                roles.add(normalized);
            }
        }
        return Set.copyOf(roles);
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "";
        }
        String normalized = role.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("role_")) {
            normalized = normalized.substring("role_".length());
        } else if (normalized.startsWith("role-")) {
            normalized = normalized.substring("role-".length());
        } else if (normalized.startsWith("role:")) {
            normalized = normalized.substring("role:".length());
        }
        return normalized.replace('-', '_').replace(' ', '_');
    }
}
