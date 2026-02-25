package com.rumal.order_service.controller;

import com.rumal.order_service.dto.analytics.*;
import com.rumal.order_service.security.InternalRequestVerifier;
import com.rumal.order_service.service.OrderAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/orders/analytics")
@RequiredArgsConstructor
public class InternalOrderAnalyticsController {

    private final InternalRequestVerifier internalRequestVerifier;
    private final OrderAnalyticsService orderAnalyticsService;

    @GetMapping("/platform/summary")
    public PlatformOrderSummary platformSummary(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestParam(defaultValue = "30") int periodDays) {
        internalRequestVerifier.verify(internalAuth);
        return orderAnalyticsService.getPlatformSummary(periodDays);
    }

    @GetMapping("/platform/revenue-trend")
    public List<DailyRevenueBucket> revenueTrend(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestParam(defaultValue = "30") int days) {
        internalRequestVerifier.verify(internalAuth);
        return orderAnalyticsService.getRevenueTrend(days);
    }

    @GetMapping("/platform/top-products")
    public List<TopProductEntry> topProducts(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestParam(defaultValue = "20") int limit) {
        internalRequestVerifier.verify(internalAuth);
        return orderAnalyticsService.getTopProducts(limit);
    }

    @GetMapping("/platform/status-breakdown")
    public Map<String, Long> statusBreakdown(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth) {
        internalRequestVerifier.verify(internalAuth);
        return orderAnalyticsService.getStatusBreakdown();
    }

    @GetMapping("/vendors/{vendorId}/summary")
    public VendorOrderSummary vendorSummary(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID vendorId,
            @RequestParam(defaultValue = "30") int periodDays) {
        internalRequestVerifier.verify(internalAuth);
        return orderAnalyticsService.getVendorSummary(vendorId, periodDays);
    }

    @GetMapping("/vendors/{vendorId}/revenue-trend")
    public List<DailyRevenueBucket> vendorRevenueTrend(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID vendorId,
            @RequestParam(defaultValue = "30") int days) {
        internalRequestVerifier.verify(internalAuth);
        return orderAnalyticsService.getVendorRevenueTrend(vendorId, days);
    }

    @GetMapping("/vendors/{vendorId}/top-products")
    public List<TopProductEntry> vendorTopProducts(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID vendorId,
            @RequestParam(defaultValue = "10") int limit) {
        internalRequestVerifier.verify(internalAuth);
        return orderAnalyticsService.getVendorTopProducts(vendorId, limit);
    }

    @GetMapping("/customers/{customerId}/summary")
    public CustomerOrderSummary customerSummary(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID customerId) {
        internalRequestVerifier.verify(internalAuth);
        return orderAnalyticsService.getCustomerSummary(customerId);
    }

    @GetMapping("/customers/{customerId}/spending-trend")
    public List<MonthlySpendBucket> customerSpendingTrend(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID customerId,
            @RequestParam(defaultValue = "12") int months) {
        internalRequestVerifier.verify(internalAuth);
        return orderAnalyticsService.getCustomerSpendingTrend(customerId, months);
    }
}
