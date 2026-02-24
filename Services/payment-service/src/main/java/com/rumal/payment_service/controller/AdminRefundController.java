package com.rumal.payment_service.controller;

import com.rumal.payment_service.dto.RefundAdminFinalizeRequest;
import com.rumal.payment_service.dto.RefundRequestResponse;
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
@RequestMapping("/admin/payments/refunds")
@RequiredArgsConstructor
public class AdminRefundController {

    private final RefundService refundService;
    private final InternalRequestVerifier internalRequestVerifier;

    @GetMapping
    public Page<RefundRequestResponse> listRefunds(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestParam(required = false) UUID vendorId,
            @RequestParam(required = false) RefundStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        internalRequestVerifier.verify(internalAuth);
        requireUserSub(userSub);
        return refundService.listAllRefunds(vendorId, status, pageable);
    }

    @GetMapping("/{id}")
    public RefundRequestResponse getRefund(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        requireUserSub(userSub);
        return refundService.getRefundById(id);
    }

    @PostMapping("/{id}/finalize")
    public RefundRequestResponse finalizeRefund(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @PathVariable UUID id,
            @Valid @RequestBody RefundAdminFinalizeRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        String adminKeycloakId = requireUserSub(userSub);
        return refundService.adminFinalize(adminKeycloakId, id, request);
    }

    private String requireUserSub(String userSub) {
        if (userSub == null || userSub.isBlank()) {
            throw new UnauthorizedException("Missing authentication header");
        }
        return userSub.trim();
    }
}
