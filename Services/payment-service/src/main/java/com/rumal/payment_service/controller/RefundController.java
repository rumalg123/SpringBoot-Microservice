package com.rumal.payment_service.controller;

import com.rumal.payment_service.dto.RefundRequestCreateRequest;
import com.rumal.payment_service.dto.RefundRequestResponse;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/payments/me/refunds")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;
    private final InternalRequestVerifier internalRequestVerifier;

    @PostMapping
    public RefundRequestResponse createRefund(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String emailVerified,
            @Valid @RequestBody RefundRequestCreateRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(emailVerified);
        String keycloakId = requireUserSub(userSub);
        return refundService.createRefundRequest(keycloakId, request);
    }

    @GetMapping
    public Page<RefundRequestResponse> listMyRefunds(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String emailVerified,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(emailVerified);
        String keycloakId = requireUserSub(userSub);
        return refundService.listRefundsForCustomer(keycloakId, pageable);
    }

    @GetMapping("/{refundId}")
    public RefundRequestResponse getRefund(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String emailVerified,
            @PathVariable UUID refundId
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(emailVerified);
        String keycloakId = requireUserSub(userSub);
        return refundService.getRefundByIdAndCustomer(refundId, keycloakId);
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
