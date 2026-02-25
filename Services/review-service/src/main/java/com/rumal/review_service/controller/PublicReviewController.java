package com.rumal.review_service.controller;

import com.rumal.review_service.dto.ReviewResponse;
import com.rumal.review_service.dto.ReviewSummaryResponse;
import com.rumal.review_service.service.ReviewImageStorageService;
import com.rumal.review_service.service.ReviewService;
import com.rumal.review_service.service.StoredImage;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.HandlerMapping;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/reviews")
@RequiredArgsConstructor
public class PublicReviewController {

    private final ReviewService reviewService;
    private final ReviewImageStorageService reviewImageStorageService;

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

    @GetMapping("/images/**")
    public ResponseEntity<byte[]> getImage(HttpServletRequest request) {
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String bestPattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String key = new AntPathMatcher().extractPathWithinPattern(bestPattern, path);
        StoredImage image = reviewImageStorageService.getImage(key);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .contentType(MediaType.parseMediaType(image.contentType()))
                .body(image.bytes());
    }
}
