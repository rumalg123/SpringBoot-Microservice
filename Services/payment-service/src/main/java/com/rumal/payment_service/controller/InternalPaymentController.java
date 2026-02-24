package com.rumal.payment_service.controller;

import com.rumal.payment_service.dto.PaymentResponse;
import com.rumal.payment_service.security.InternalRequestVerifier;
import com.rumal.payment_service.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/payments")
@RequiredArgsConstructor
public class InternalPaymentController {

    private final PaymentService paymentService;
    private final InternalRequestVerifier internalRequestVerifier;

    @GetMapping("/order/{orderId}")
    public PaymentResponse getPaymentByOrder(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID orderId
    ) {
        internalRequestVerifier.verify(internalAuth);
        return paymentService.getPaymentByOrder(orderId);
    }
}
