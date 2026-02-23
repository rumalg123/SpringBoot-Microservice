package com.rumal.access_service.controller;

import com.rumal.access_service.dto.UpsertVendorStaffAccessRequest;
import com.rumal.access_service.dto.VendorStaffAccessResponse;
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
@RequestMapping("/admin/vendor-staff")
@RequiredArgsConstructor
public class AdminVendorStaffController {

    private static final String INTERNAL_HEADER = "X-Internal-Auth";
    private final InternalRequestVerifier internalRequestVerifier;
    private final AccessService accessService;

    @GetMapping
    public List<VendorStaffAccessResponse> listAll(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @RequestParam(required = false) UUID vendorId
    ) {
        internalRequestVerifier.verify(internalAuth);
        return accessService.listVendorStaff(vendorId);
    }

    @GetMapping("/deleted")
    public List<VendorStaffAccessResponse> listDeleted(@RequestHeader(INTERNAL_HEADER) String internalAuth) {
        internalRequestVerifier.verify(internalAuth);
        return accessService.listDeletedVendorStaff();
    }

    @GetMapping("/{id}")
    public VendorStaffAccessResponse getById(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        return accessService.getVendorStaffById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VendorStaffAccessResponse create(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "X-Action-Reason", required = false) String actionReason,
            @Valid @RequestBody UpsertVendorStaffAccessRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        return accessService.createVendorStaff(request, userSub, userRoles, actionReason);
    }

    @PutMapping("/{id}")
    public VendorStaffAccessResponse update(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "X-Action-Reason", required = false) String actionReason,
            @PathVariable UUID id,
            @Valid @RequestBody UpsertVendorStaffAccessRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        return accessService.updateVendorStaff(id, request, userSub, userRoles, actionReason);
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
        accessService.softDeleteVendorStaff(id, userSub, userRoles, actionReason);
    }

    @PostMapping("/{id}/restore")
    public VendorStaffAccessResponse restore(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "X-Action-Reason", required = false) String actionReason,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        return accessService.restoreVendorStaff(id, userSub, userRoles, actionReason);
    }
}
