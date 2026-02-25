package com.rumal.payment_service.controller;

import com.rumal.payment_service.dto.analytics.*;
import com.rumal.payment_service.security.InternalRequestVerifier;
import com.rumal.payment_service.service.PaymentAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/internal/payments/analytics")
@RequiredArgsConstructor
public class InternalPaymentAnalyticsController {

    private final InternalRequestVerifier internalRequestVerifier;
    private final PaymentAnalyticsService paymentAnalyticsService;

    @GetMapping("/platform/summary")
    public PaymentPlatformSummary platformSummary(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth) {
        internalRequestVerifier.verify(internalAuth);
        return paymentAnalyticsService.getPlatformSummary();
    }

    @GetMapping("/platform/method-breakdown")
    public List<PaymentMethodBreakdown> methodBreakdown(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth) {
        internalRequestVerifier.verify(internalAuth);
        return paymentAnalyticsService.getMethodBreakdown();
    }
}
