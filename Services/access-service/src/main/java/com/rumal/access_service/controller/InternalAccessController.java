package com.rumal.access_service.controller;

import com.rumal.access_service.dto.PlatformAccessLookupResponse;
import com.rumal.access_service.dto.VendorStaffAccessLookupResponse;
import com.rumal.access_service.security.InternalRequestVerifier;
import com.rumal.access_service.service.AccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/access")
@RequiredArgsConstructor
public class InternalAccessController {

    private static final String INTERNAL_HEADER = "X-Internal-Auth";
    private final InternalRequestVerifier internalRequestVerifier;
    private final AccessService accessService;

    @GetMapping("/platform/by-keycloak/{keycloakUserId}")
    public PlatformAccessLookupResponse getPlatformAccess(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @PathVariable String keycloakUserId
    ) {
        internalRequestVerifier.verify(internalAuth);
        return accessService.getPlatformAccessByKeycloakUser(keycloakUserId);
    }

    @GetMapping("/vendors/by-keycloak/{keycloakUserId}")
    public List<VendorStaffAccessLookupResponse> listVendorStaffAccess(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @PathVariable String keycloakUserId
    ) {
        internalRequestVerifier.verify(internalAuth);
        return accessService.listVendorStaffAccessByKeycloakUser(keycloakUserId);
    }
}
