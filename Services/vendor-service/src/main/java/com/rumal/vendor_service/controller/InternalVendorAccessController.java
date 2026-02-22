package com.rumal.vendor_service.controller;

import com.rumal.vendor_service.dto.VendorAccessMembershipResponse;
import com.rumal.vendor_service.security.InternalRequestVerifier;
import com.rumal.vendor_service.service.VendorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/vendors/access")
@RequiredArgsConstructor
public class InternalVendorAccessController {

    private static final String INTERNAL_HEADER = "X-Internal-Auth";

    private final VendorService vendorService;
    private final InternalRequestVerifier internalRequestVerifier;

    @GetMapping("/by-keycloak/{keycloakUserId}")
    public List<VendorAccessMembershipResponse> listByKeycloakUser(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @PathVariable String keycloakUserId
    ) {
        internalRequestVerifier.verify(internalAuth);
        return vendorService.listAccessibleVendorMembershipsByKeycloakUser(keycloakUserId);
    }
}
