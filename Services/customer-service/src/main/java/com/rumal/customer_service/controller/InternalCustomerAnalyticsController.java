package com.rumal.customer_service.controller;

import com.rumal.customer_service.dto.analytics.*;
import com.rumal.customer_service.security.InternalRequestVerifier;
import com.rumal.customer_service.service.CustomerAnalyticsService;
import jakarta.validation.constraints.Max;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/internal/customers/analytics")
@RequiredArgsConstructor
public class InternalCustomerAnalyticsController {

    private final InternalRequestVerifier internalRequestVerifier;
    private final CustomerAnalyticsService customerAnalyticsService;

    @GetMapping("/platform/summary")
    public CustomerPlatformSummary platformSummary(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth) {
        internalRequestVerifier.verify(internalAuth);
        return customerAnalyticsService.getPlatformSummary();
    }

    @GetMapping("/platform/growth-trend")
    public List<MonthlyGrowthBucket> growthTrend(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestParam(defaultValue = "12") @Max(120) int months) {
        internalRequestVerifier.verify(internalAuth);
        return customerAnalyticsService.getGrowthTrend(months);
    }

    @GetMapping("/{customerId}/profile-summary")
    public CustomerProfileSummary profileSummary(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID customerId) {
        internalRequestVerifier.verify(internalAuth);
        return customerAnalyticsService.getProfileSummary(customerId);
    }
}
