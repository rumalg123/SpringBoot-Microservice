package com.rumal.review_service.controller;

import com.rumal.review_service.dto.ReviewSummaryResponse;
import com.rumal.review_service.security.InternalRequestVerifier;
import com.rumal.review_service.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/internal/reviews")
@RequiredArgsConstructor
public class InternalReviewController {

    private static final String INTERNAL_HEADER = "X-Internal-Auth";

    private final ReviewService reviewService;
    private final InternalRequestVerifier internalRequestVerifier;

    @GetMapping("/products/{productId}/summary")
    public ReviewSummaryResponse getProductSummary(
            @RequestHeader(INTERNAL_HEADER) String internalAuth,
            @PathVariable UUID productId
    ) {
        internalRequestVerifier.verify(internalAuth);
        return reviewService.getProductReviewSummary(productId);
    }
}
