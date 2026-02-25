package com.rumal.promotion_service.controller;

import com.rumal.promotion_service.dto.analytics.*;
import com.rumal.promotion_service.security.InternalRequestVerifier;
import com.rumal.promotion_service.service.InternalPromotionAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/promotions/analytics")
@RequiredArgsConstructor
public class InternalPromotionAnalyticsController {

    private final InternalRequestVerifier internalRequestVerifier;
    private final InternalPromotionAnalyticsService promotionAnalyticsService;

    @GetMapping("/platform/summary")
    public PromotionPlatformSummary platformSummary(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth) {
        internalRequestVerifier.verify(internalAuth);
        return promotionAnalyticsService.getPlatformSummary();
    }

    @GetMapping("/platform/roi")
    public List<PromotionRoiEntry> promotionRoi(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestParam(defaultValue = "20") int limit) {
        internalRequestVerifier.verify(internalAuth);
        return promotionAnalyticsService.getPromotionRoi(limit);
    }

    @GetMapping("/vendors/{vendorId}/summary")
    public VendorPromotionSummary vendorSummary(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID vendorId) {
        internalRequestVerifier.verify(internalAuth);
        return promotionAnalyticsService.getVendorSummary(vendorId);
    }
}
