package com.rumal.payment_service.controller;

import com.rumal.payment_service.dto.CompletePayoutRequest;
import com.rumal.payment_service.dto.CreatePayoutRequest;
import com.rumal.payment_service.dto.VendorPayoutResponse;
import com.rumal.payment_service.entity.PayoutStatus;
import com.rumal.payment_service.exception.UnauthorizedException;
import com.rumal.payment_service.security.InternalRequestVerifier;
import com.rumal.payment_service.service.PayoutService;
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
@RequestMapping("/admin/payments/payouts")
@RequiredArgsConstructor
public class AdminPayoutController {

    private final PayoutService payoutService;
    private final InternalRequestVerifier internalRequestVerifier;

    @GetMapping
    public Page<VendorPayoutResponse> listPayouts(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestParam(required = false) UUID vendorId,
            @RequestParam(required = false) PayoutStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        internalRequestVerifier.verify(internalAuth);
        requireUserSub(userSub);
        return payoutService.listPayouts(vendorId, status, pageable);
    }

    @GetMapping("/{id}")
    public VendorPayoutResponse getPayout(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        requireUserSub(userSub);
        return payoutService.getPayoutById(id);
    }

    @PostMapping
    public VendorPayoutResponse createPayout(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @Valid @RequestBody CreatePayoutRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        String adminKeycloakId = requireUserSub(userSub);
        return payoutService.createPayout(adminKeycloakId, request);
    }

    @PostMapping("/{id}/approve")
    public VendorPayoutResponse approvePayout(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        String adminKeycloakId = requireUserSub(userSub);
        return payoutService.approvePayout(adminKeycloakId, id);
    }

    @PostMapping("/{id}/complete")
    public VendorPayoutResponse completePayout(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @PathVariable UUID id,
            @Valid @RequestBody CompletePayoutRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        String adminKeycloakId = requireUserSub(userSub);
        return payoutService.completePayout(adminKeycloakId, id, request);
    }

    @PostMapping("/{id}/cancel")
    public VendorPayoutResponse cancelPayout(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @PathVariable UUID id,
            @RequestParam(required = false) String reason
    ) {
        internalRequestVerifier.verify(internalAuth);
        String adminKeycloakId = requireUserSub(userSub);
        return payoutService.cancelPayout(adminKeycloakId, id, reason);
    }

    private String requireUserSub(String userSub) {
        if (userSub == null || userSub.isBlank()) {
            throw new UnauthorizedException("Missing authentication header");
        }
        return userSub.trim();
    }
}
