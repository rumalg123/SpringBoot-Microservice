package com.rumal.payment_service.controller;

import com.rumal.payment_service.dto.VendorPayoutResponse;
import com.rumal.payment_service.entity.PayoutStatus;
import com.rumal.payment_service.exception.ResourceNotFoundException;
import com.rumal.payment_service.exception.UnauthorizedException;
import com.rumal.payment_service.security.InternalRequestVerifier;
import com.rumal.payment_service.service.PaymentAccessScopeService;
import com.rumal.payment_service.service.PayoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/payments/vendor/me/payouts")
@RequiredArgsConstructor
public class VendorPayoutController {

    private final PayoutService payoutService;
    private final InternalRequestVerifier internalRequestVerifier;
    private final PaymentAccessScopeService paymentAccessScopeService;

    @GetMapping
    public Page<VendorPayoutResponse> listPayouts(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String emailVerified,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(required = false) UUID vendorId,
            @RequestParam(required = false) PayoutStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(emailVerified);
        var scope = paymentAccessScopeService.resolveScope(requireUserSub(userSub), userRoles, internalAuth);
        UUID resolvedVendorId = paymentAccessScopeService.resolveVendorIdForVendorFinanceRead(scope, vendorId);
        return payoutService.listPayouts(resolvedVendorId, status, pageable);
    }

    @GetMapping("/{id}")
    public VendorPayoutResponse getPayout(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String emailVerified,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(required = false) UUID vendorId,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(emailVerified);
        var scope = paymentAccessScopeService.resolveScope(requireUserSub(userSub), userRoles, internalAuth);
        UUID resolvedVendorId = paymentAccessScopeService.resolveVendorIdForVendorFinanceRead(scope, vendorId);
        VendorPayoutResponse payout = payoutService.getPayoutById(id);
        if (!resolvedVendorId.equals(payout.vendorId())) {
            throw new ResourceNotFoundException("Payout not found: " + id);
        }
        return payout;
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
}
