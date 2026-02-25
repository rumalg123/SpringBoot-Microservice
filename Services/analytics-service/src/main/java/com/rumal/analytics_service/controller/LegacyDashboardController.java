package com.rumal.analytics_service.controller;

import com.rumal.analytics_service.dto.DashboardSummaryResponse;
import com.rumal.analytics_service.security.InternalRequestVerifier;
import com.rumal.analytics_service.service.AdminAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class LegacyDashboardController {

    private final InternalRequestVerifier internalRequestVerifier;
    private final AdminAnalyticsService adminAnalyticsService;

    @GetMapping("/summary")
    public DashboardSummaryResponse summary(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth) {
        internalRequestVerifier.verify(internalAuth);
        return adminAnalyticsService.getLegacyDashboardSummary();
    }
}
