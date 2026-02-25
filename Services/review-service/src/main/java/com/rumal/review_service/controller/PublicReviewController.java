package com.rumal.review_service.controller;

import com.rumal.review_service.dto.ReviewResponse;
import com.rumal.review_service.dto.ReviewSummaryResponse;
import com.rumal.review_service.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/reviews")
@RequiredArgsConstructor
public class PublicReviewController {

    private final ReviewService reviewService;

    @GetMapping("/products/{productId}")
    public Page<ReviewResponse> listByProduct(
            @PathVariable UUID productId,
            @RequestParam(required = false, defaultValue = "recent") String sortBy,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return reviewService.listByProduct(productId, pageable, sortBy);
    }

    @GetMapping("/products/{productId}/summary")
    public ReviewSummaryResponse getProductSummary(@PathVariable UUID productId) {
        return reviewService.getProductReviewSummary(productId);
    }

    @GetMapping("/{reviewId}")
    public ReviewResponse getById(@PathVariable UUID reviewId) {
        return reviewService.getById(reviewId);
    }
}
