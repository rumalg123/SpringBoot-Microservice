package com.rumal.cart_service.controller;

import com.rumal.cart_service.dto.analytics.CartPlatformSummary;
import com.rumal.cart_service.security.InternalRequestVerifier;
import com.rumal.cart_service.service.CartAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/cart/analytics")
@RequiredArgsConstructor
public class InternalCartAnalyticsController {

    private final InternalRequestVerifier internalRequestVerifier;
    private final CartAnalyticsService cartAnalyticsService;

    @GetMapping("/platform/summary")
    public CartPlatformSummary platformSummary(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth) {
        internalRequestVerifier.verify(internalAuth);
        return cartAnalyticsService.getPlatformSummary();
    }
}
