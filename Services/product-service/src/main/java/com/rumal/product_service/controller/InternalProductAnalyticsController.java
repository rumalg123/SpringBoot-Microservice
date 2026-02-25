package com.rumal.product_service.controller;

import com.rumal.product_service.dto.analytics.*;
import com.rumal.product_service.security.InternalRequestVerifier;
import com.rumal.product_service.service.ProductAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/products/analytics")
@RequiredArgsConstructor
public class InternalProductAnalyticsController {

    private final InternalRequestVerifier internalRequestVerifier;
    private final ProductAnalyticsService productAnalyticsService;

    @GetMapping("/platform/summary")
    public ProductPlatformSummary platformSummary(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth) {
        internalRequestVerifier.verify(internalAuth);
        return productAnalyticsService.getPlatformSummary();
    }

    @GetMapping("/platform/top-viewed")
    public List<ProductViewEntry> topViewed(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestParam(defaultValue = "20") int limit) {
        internalRequestVerifier.verify(internalAuth);
        return productAnalyticsService.getTopViewed(limit);
    }

    @GetMapping("/platform/top-sold")
    public List<ProductSoldEntry> topSold(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestParam(defaultValue = "20") int limit) {
        internalRequestVerifier.verify(internalAuth);
        return productAnalyticsService.getTopSold(limit);
    }

    @GetMapping("/platform/approval-breakdown")
    public Map<String, Long> approvalBreakdown(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth) {
        internalRequestVerifier.verify(internalAuth);
        return productAnalyticsService.getApprovalBreakdown();
    }

    @GetMapping("/vendors/{vendorId}/summary")
    public VendorProductSummary vendorSummary(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID vendorId) {
        internalRequestVerifier.verify(internalAuth);
        return productAnalyticsService.getVendorSummary(vendorId);
    }
}
