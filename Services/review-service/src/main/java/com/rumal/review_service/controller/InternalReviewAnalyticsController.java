package com.rumal.review_service.controller;

import com.rumal.review_service.dto.analytics.*;
import com.rumal.review_service.security.InternalRequestVerifier;
import com.rumal.review_service.service.ReviewAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/reviews/analytics")
@RequiredArgsConstructor
public class InternalReviewAnalyticsController {

    private final InternalRequestVerifier internalRequestVerifier;
    private final ReviewAnalyticsService reviewAnalyticsService;

    @GetMapping("/platform/summary")
    public ReviewPlatformSummary platformSummary(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth) {
        internalRequestVerifier.verify(internalAuth);
        return reviewAnalyticsService.getPlatformSummary();
    }

    @GetMapping("/platform/rating-distribution")
    public Map<Integer, Long> ratingDistribution(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth) {
        internalRequestVerifier.verify(internalAuth);
        return reviewAnalyticsService.getRatingDistribution();
    }

    @GetMapping("/vendors/{vendorId}/summary")
    public VendorReviewSummary vendorSummary(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @PathVariable UUID vendorId) {
        internalRequestVerifier.verify(internalAuth);
        return reviewAnalyticsService.getVendorSummary(vendorId);
    }
}
