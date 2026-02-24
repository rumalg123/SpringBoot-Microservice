package com.rumal.payment_service.controller;

import com.rumal.payment_service.dto.RefundRequestResponse;
import com.rumal.payment_service.dto.RefundVendorResponseRequest;
import com.rumal.payment_service.entity.RefundStatus;
import com.rumal.payment_service.exception.UnauthorizedException;
import com.rumal.payment_service.security.InternalRequestVerifier;
import com.rumal.payment_service.service.RefundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/payments/vendor/me/refunds")
@RequiredArgsConstructor
public class VendorRefundController {

    private final RefundService refundService;
    private final InternalRequestVerifier internalRequestVerifier;

    @GetMapping
    public Page<RefundRequestResponse> listVendorRefunds(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String emailVerified,
            @RequestHeader(value = "X-User-Vendor-Id", required = false) String vendorIdHeader,
            @RequestParam(required = false) RefundStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(emailVerified);
        requireUserSub(userSub);
        UUID vendorId = requireVendorId(vendorIdHeader);
        return refundService.listRefundsForVendor(vendorId, status, pageable);
    }

    @GetMapping("/{refundId}")
    public RefundRequestResponse getVendorRefund(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String emailVerified,
            @RequestHeader(value = "X-User-Vendor-Id", required = false) String vendorIdHeader,
            @PathVariable UUID refundId
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(emailVerified);
        requireUserSub(userSub);
        UUID vendorId = requireVendorId(vendorIdHeader);
        return refundService.getRefundByIdAndVendor(refundId, vendorId);
    }

    @PostMapping("/{refundId}/respond")
    public RefundRequestResponse respondToRefund(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String emailVerified,
            @RequestHeader(value = "X-User-Vendor-Id", required = false) String vendorIdHeader,
            @PathVariable UUID refundId,
            @Valid @RequestBody RefundVendorResponseRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(emailVerified);
        String keycloakId = requireUserSub(userSub);
        UUID vendorId = requireVendorId(vendorIdHeader);
        return refundService.vendorRespond(keycloakId, vendorId, refundId, request);
    }

    private String requireUserSub(String userSub) {
        if (userSub == null || userSub.isBlank()) {
            throw new UnauthorizedException("Missing authentication header");
        }
        return userSub.trim();
    }

    private void verifyEmailVerified(String emailVerified) {
        if (emailVerified == null || !"true".equalsIgnoreCase(emailVerified.trim())) {
            throw new UnauthorizedException("Email is not verified");
        }
    }

    private UUID requireVendorId(String vendorIdHeader) {
        if (vendorIdHeader == null || vendorIdHeader.isBlank()) {
            throw new UnauthorizedException("Missing vendor identification header");
        }
        try {
            return UUID.fromString(vendorIdHeader.trim());
        } catch (IllegalArgumentException e) {
            throw new UnauthorizedException("Invalid vendor identification header");
        }
    }
}
