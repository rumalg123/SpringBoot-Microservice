package com.rumal.vendor_service.controller;

import com.rumal.vendor_service.dto.analytics.*;
import com.rumal.vendor_service.security.InternalRequestVerifier;
import com.rumal.vendor_service.service.VendorAnalyticsService;
import jakarta.validation.constraints.Max;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/internal/vendors/analytics")
@RequiredArgsConstructor
public class InternalVendorAnalyticsController {

    private final InternalRequestVerifier internalRequestVerifier;
    private final VendorAnalyticsService vendorAnalyticsService;

    @GetMapping("/platform/summary")
    public VendorPlatformSummary platformSummary(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth) {
        internalRequestVerifier.verify(internalAuth);
        return vendorAnalyticsService.getPlatformSummary();
    }

    @GetMapping("/platform/leaderboard")
    public List<VendorLeaderboardEntry> leaderboard(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestParam(defaultValue = "ORDERS_COMPLETED") String sortBy,
            @RequestParam(defaultValue = "20") @Max(100) int limit) {
        internalRequestVerifier.verify(internalAuth);
        return vendorAnalyticsService.getLeaderboard(sortBy, limit);
    }

    @GetMapping("/{vendorId}/performance")
    public VendorPerformanceSummary vendorPerformance(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID vendorId) {
        internalRequestVerifier.verify(internalAuth);
        return vendorAnalyticsService.getPerformance(vendorId);
    }
}
