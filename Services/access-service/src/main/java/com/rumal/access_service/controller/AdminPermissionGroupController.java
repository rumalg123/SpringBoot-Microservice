package com.rumal.access_service.controller;

import com.rumal.access_service.dto.PermissionGroupResponse;
import com.rumal.access_service.dto.UpsertPermissionGroupRequest;
import com.rumal.access_service.entity.PermissionGroupScope;
import com.rumal.access_service.security.InternalRequestVerifier;
import com.rumal.access_service.service.AccessService;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/permission-groups")
@RequiredArgsConstructor
public class AdminPermissionGroupController {

    private static final String INTERNAL_HEADER = "X-Internal-Auth";
    private final InternalRequestVerifier internalRequestVerifier;
    private final AccessService accessService;

    @GetMapping
    public List<PermissionGroupResponse> listAll(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @RequestParam(required = false) PermissionGroupScope scope
    ) {
        internalRequestVerifier.verify(internalAuth);
        return accessService.listPermissionGroups(scope);
    }

    @GetMapping("/{id}")
    public PermissionGroupResponse getById(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        return accessService.getPermissionGroupById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PermissionGroupResponse create(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @Valid @RequestBody UpsertPermissionGroupRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        return accessService.createPermissionGroup(request);
    }

    @PutMapping("/{id}")
    public PermissionGroupResponse update(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @PathVariable UUID id,
            @Valid @RequestBody UpsertPermissionGroupRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        return accessService.updatePermissionGroup(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        accessService.deletePermissionGroup(id);
    }
}
