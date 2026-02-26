package com.rumal.payment_service.controller;

import com.rumal.payment_service.dto.InitiatePaymentRequest;
import com.rumal.payment_service.dto.PayHereCheckoutFormData;
import com.rumal.payment_service.dto.PaymentResponse;
import com.rumal.payment_service.exception.UnauthorizedException;
import com.rumal.payment_service.security.InternalRequestVerifier;
import com.rumal.payment_service.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/payments/me")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final InternalRequestVerifier internalRequestVerifier;

    @PostMapping("/initiate")
    public PayHereCheckoutFormData initiate(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String emailVerified,
            @Valid @RequestBody InitiatePaymentRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(emailVerified);
        String keycloakId = requireUserSub(userSub);
        return paymentService.initiatePayment(keycloakId, request.orderId());
    }

    @GetMapping("/{paymentId}")
    public PaymentResponse getPayment(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String emailVerified,
            @PathVariable UUID paymentId
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(emailVerified);
        String keycloakId = requireUserSub(userSub);
        return paymentService.getPaymentById(paymentId, keycloakId);
    }

    @GetMapping("/order/{orderId}")
    public PaymentResponse getPaymentByOrder(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String emailVerified,
            @PathVariable UUID orderId
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(emailVerified);
        String keycloakId = requireUserSub(userSub);
        return paymentService.getPaymentByOrder(orderId, keycloakId);
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
