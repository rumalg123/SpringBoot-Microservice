package com.rumal.vendor_service.controller;

import com.rumal.vendor_service.dto.RequestVerificationRequest;
import com.rumal.vendor_service.dto.UpdateVendorSelfServiceRequest;
import com.rumal.vendor_service.dto.UpsertVendorPayoutConfigRequest;
import com.rumal.vendor_service.dto.VendorPayoutConfigResponse;
import com.rumal.vendor_service.dto.VendorResponse;
import com.rumal.vendor_service.exception.UnauthorizedException;
import com.rumal.vendor_service.security.InternalRequestVerifier;
import com.rumal.vendor_service.service.VendorService;
import com.rumal.vendor_service.service.VendorSelfAccessScopeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/vendors/me")
@RequiredArgsConstructor
public class VendorSelfServiceController {

    private final VendorService vendorService;
    private final InternalRequestVerifier internalRequestVerifier;
    private final VendorSelfAccessScopeService vendorSelfAccessScopeService;

    @GetMapping
    public VendorResponse getMyVendor(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestParam(required = false) UUID vendorId
    ) {
        verifyAuth(internalAuth, userSub);
        UUID resolvedVendorId = vendorSelfAccessScopeService.resolveVendorIdForView(userSub, userRoles, internalAuth, vendorId);
        return vendorService.getVendorForKeycloakUser(userSub, resolvedVendorId);
    }

    @PutMapping
    public VendorResponse updateMyVendor(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestParam(required = false) UUID vendorId,
            @Valid @RequestBody UpdateVendorSelfServiceRequest request
    ) {
        verifyAuth(internalAuth, userSub);
        UUID resolvedVendorId = vendorSelfAccessScopeService.resolveVendorIdForSettingsManage(userSub, userRoles, internalAuth, vendorId);
        return vendorService.updateVendorSelfService(userSub, resolvedVendorId, request);
    }

    @GetMapping("/payout-config")
    public VendorPayoutConfigResponse getMyPayoutConfig(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestParam(required = false) UUID vendorId
    ) {
        verifyAuth(internalAuth, userSub);
        UUID resolvedVendorId = vendorSelfAccessScopeService.resolveVendorIdForOwner(userSub, userRoles, vendorId);
        return vendorService.getPayoutConfig(userSub, resolvedVendorId);
    }

    @PutMapping("/payout-config")
    public VendorPayoutConfigResponse upsertMyPayoutConfig(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestParam(required = false) UUID vendorId,
            @Valid @RequestBody UpsertVendorPayoutConfigRequest request
    ) {
        verifyAuth(internalAuth, userSub);
        UUID resolvedVendorId = vendorSelfAccessScopeService.resolveVendorIdForOwner(userSub, userRoles, vendorId);
        return vendorService.upsertPayoutConfig(userSub, resolvedVendorId, request);
    }

    @PostMapping("/request-verification")
    public VendorResponse requestVerification(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestParam(required = false) UUID vendorId,
            @Valid @RequestBody(required = false) RequestVerificationRequest request
    ) {
        verifyAuth(internalAuth, userSub);
        UUID resolvedVendorId = vendorSelfAccessScopeService.resolveVendorIdForSettingsManage(userSub, userRoles, internalAuth, vendorId);
        return vendorService.requestVerification(userSub, resolvedVendorId, request);
    }

    @PostMapping("/stop-orders")
    public VendorResponse stopOrders(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestParam(required = false) UUID vendorId
    ) {
        verifyAuth(internalAuth, userSub);
        UUID resolvedVendorId = vendorSelfAccessScopeService.resolveVendorIdForOrderManage(userSub, userRoles, internalAuth, vendorId);
        return vendorService.selfServiceStopOrders(userSub, resolvedVendorId);
    }

    @PostMapping("/resume-orders")
    public VendorResponse resumeOrders(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestParam(required = false) UUID vendorId
    ) {
        verifyAuth(internalAuth, userSub);
        UUID resolvedVendorId = vendorSelfAccessScopeService.resolveVendorIdForOrderManage(userSub, userRoles, internalAuth, vendorId);
        return vendorService.selfServiceResumeOrders(userSub, resolvedVendorId);
    }

    private void verifyAuth(String internalAuth, String userSub) {
        internalRequestVerifier.verify(internalAuth);
        if (userSub == null || userSub.isBlank()) {
            throw new UnauthorizedException("Missing authentication header");
        }
    }
}
