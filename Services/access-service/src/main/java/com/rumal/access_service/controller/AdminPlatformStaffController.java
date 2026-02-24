package com.rumal.access_service.controller;

import com.rumal.access_service.dto.PlatformStaffAccessResponse;
import com.rumal.access_service.dto.UpsertPlatformStaffAccessRequest;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/admin/platform-staff")
@RequiredArgsConstructor
public class AdminPlatformStaffController {

    private static final String INTERNAL_HEADER = "X-Internal-Auth";
    private final InternalRequestVerifier internalRequestVerifier;
    private final AccessService accessService;

    @GetMapping
    public Page<PlatformStaffAccessResponse> listAll(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @PageableDefault(size = 20, sort = "email") Pageable pageable
    ) {
        internalRequestVerifier.verify(internalAuth);
        return accessService.listPlatformStaff(pageable);
    }

    @GetMapping("/deleted")
    public Page<PlatformStaffAccessResponse> listDeleted(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @PageableDefault(size = 20, sort = "email") Pageable pageable
    ) {
        internalRequestVerifier.verify(internalAuth);
        return accessService.listDeletedPlatformStaff(pageable);
    }

    @GetMapping("/{id}")
    public PlatformStaffAccessResponse getById(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        return accessService.getPlatformStaffById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlatformStaffAccessResponse create(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "X-Action-Reason", required = false) String actionReason,
            @Valid @RequestBody UpsertPlatformStaffAccessRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        return accessService.createPlatformStaff(request, userSub, userRoles, actionReason);
    }

    @PutMapping("/{id}")
    public PlatformStaffAccessResponse update(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "X-Action-Reason", required = false) String actionReason,
            @PathVariable UUID id,
            @Valid @RequestBody UpsertPlatformStaffAccessRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        return accessService.updatePlatformStaff(id, request, userSub, userRoles, actionReason);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "X-Action-Reason", required = false) String actionReason,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        accessService.softDeletePlatformStaff(id, userSub, userRoles, actionReason);
    }

    @PostMapping("/{id}/restore")
    public PlatformStaffAccessResponse restore(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "X-Action-Reason", required = false) String actionReason,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        return accessService.restorePlatformStaff(id, userSub, userRoles, actionReason);
    }
}
