package com.rumal.analytics_service.controller;

import com.rumal.analytics_service.dto.CustomerInsightsResponse;
import com.rumal.analytics_service.security.InternalRequestVerifier;
import com.rumal.analytics_service.service.CustomerAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/analytics/customer")
@RequiredArgsConstructor
public class CustomerAnalyticsController {

    private final InternalRequestVerifier internalRequestVerifier;
    private final CustomerAnalyticsService customerAnalyticsService;

    @GetMapping("/{customerId}/insights")
    public CustomerInsightsResponse customerInsights(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID customerId) {
        internalRequestVerifier.verify(internalAuth);
        return customerAnalyticsService.getCustomerInsights(customerId);
    }
}
