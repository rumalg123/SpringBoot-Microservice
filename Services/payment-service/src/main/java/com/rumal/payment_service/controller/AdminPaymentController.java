package com.rumal.payment_service.controller;

import com.rumal.payment_service.dto.PaymentAuditResponse;
import com.rumal.payment_service.dto.PaymentDetailResponse;
import com.rumal.payment_service.dto.PaymentResponse;
import com.rumal.payment_service.entity.PaymentStatus;
import com.rumal.payment_service.exception.UnauthorizedException;
import com.rumal.payment_service.security.InternalRequestVerifier;
import com.rumal.payment_service.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/admin/payments")
@RequiredArgsConstructor
public class AdminPaymentController {

    private final PaymentService paymentService;
    private final InternalRequestVerifier internalRequestVerifier;

    @GetMapping
    public Page<PaymentResponse> listPayments(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) PaymentStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        internalRequestVerifier.verify(internalAuth);
        requireUserSub(userSub);
        return paymentService.listAllPayments(customerId, status, pageable);
    }

    @GetMapping("/{id}")
    public PaymentDetailResponse getPayment(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        requireUserSub(userSub);
        return paymentService.getPaymentDetail(id);
    }

    @GetMapping("/{id}/audit")
    public Page<PaymentAuditResponse> getAuditTrail(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @PathVariable UUID id,
            @PageableDefault(size = 50, sort = "createdAt") Pageable pageable
    ) {
        internalRequestVerifier.verify(internalAuth);
        requireUserSub(userSub);
        return paymentService.getAuditTrail(id, pageable);
    }

    @PostMapping("/{id}/verify")
    public PaymentDetailResponse verifyPayment(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @PathVariable UUID id
    ) {
        internalRequestVerifier.verify(internalAuth);
        requireUserSub(userSub);
        return paymentService.verifyPaymentWithPayHere(id);
    }

    private String requireUserSub(String userSub) {
        if (userSub == null || userSub.isBlank()) {
            throw new UnauthorizedException("Missing authentication header");
        }
        return userSub.trim();
    }
}
