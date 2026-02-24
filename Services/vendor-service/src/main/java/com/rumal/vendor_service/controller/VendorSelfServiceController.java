package com.rumal.vendor_service.controller;

import com.rumal.vendor_service.dto.RequestVerificationRequest;
import com.rumal.vendor_service.dto.UpdateVendorSelfServiceRequest;
import com.rumal.vendor_service.dto.UpsertVendorPayoutConfigRequest;
import com.rumal.vendor_service.dto.VendorPayoutConfigResponse;
import com.rumal.vendor_service.dto.VendorResponse;
import com.rumal.vendor_service.exception.UnauthorizedException;
import com.rumal.vendor_service.security.InternalRequestVerifier;
import com.rumal.vendor_service.service.VendorService;
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

    @GetMapping
    public VendorResponse getMyVendor(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestParam(required = false) UUID vendorId
    ) {
        verifyAuth(internalAuth, userSub);
        return vendorService.getVendorForKeycloakUser(userSub, vendorId);
    }

    @PutMapping
    public VendorResponse updateMyVendor(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestParam(required = false) UUID vendorId,
            @Valid @RequestBody UpdateVendorSelfServiceRequest request
    ) {
        verifyAuth(internalAuth, userSub);
        return vendorService.updateVendorSelfService(userSub, vendorId, request);
    }

    @GetMapping("/payout-config")
    public VendorPayoutConfigResponse getMyPayoutConfig(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestParam(required = false) UUID vendorId
    ) {
        verifyAuth(internalAuth, userSub);
        return vendorService.getPayoutConfig(userSub, vendorId);
    }

    @PutMapping("/payout-config")
    public VendorPayoutConfigResponse upsertMyPayoutConfig(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestParam(required = false) UUID vendorId,
            @Valid @RequestBody UpsertVendorPayoutConfigRequest request
    ) {
        verifyAuth(internalAuth, userSub);
        return vendorService.upsertPayoutConfig(userSub, vendorId, request);
    }

    @PostMapping("/request-verification")
    public VendorResponse requestVerification(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestParam(required = false) UUID vendorId,
            @Valid @RequestBody(required = false) RequestVerificationRequest request
    ) {
        verifyAuth(internalAuth, userSub);
        return vendorService.requestVerification(userSub, vendorId, request);
    }

    @PostMapping("/stop-orders")
    public VendorResponse stopOrders(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestParam(required = false) UUID vendorId
    ) {
        verifyAuth(internalAuth, userSub);
        return vendorService.selfServiceStopOrders(userSub, vendorId);
    }

    @PostMapping("/resume-orders")
    public VendorResponse resumeOrders(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestParam(required = false) UUID vendorId
    ) {
        verifyAuth(internalAuth, userSub);
        return vendorService.selfServiceResumeOrders(userSub, vendorId);
    }

    private void verifyAuth(String internalAuth, String userSub) {
        internalRequestVerifier.verify(internalAuth);
        if (userSub == null || userSub.isBlank()) {
            throw new UnauthorizedException("Missing authentication header");
        }
    }
}
