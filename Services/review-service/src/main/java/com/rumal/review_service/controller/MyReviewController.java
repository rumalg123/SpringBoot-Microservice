package com.rumal.review_service.controller;

import com.rumal.review_service.client.CustomerClient;
import com.rumal.review_service.dto.*;
import com.rumal.review_service.exception.UnauthorizedException;
import com.rumal.review_service.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/reviews/me")
@RequiredArgsConstructor
public class MyReviewController {

    private final ReviewService reviewService;
    private final CustomerClient customerClient;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse create(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @Valid @RequestBody CreateReviewRequest request
    ) {
        assertAuthenticated(userSub);
        CustomerSummary customer = customerClient.getByKeycloakId(userSub);
        String displayName = buildDisplayName(customer);
        return reviewService.createReview(customer.id(), displayName, request);
    }

    @PutMapping("/{reviewId}")
    public ReviewResponse update(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @PathVariable UUID reviewId,
            @Valid @RequestBody UpdateReviewRequest request
    ) {
        assertAuthenticated(userSub);
        CustomerSummary customer = customerClient.getByKeycloakId(userSub);
        return reviewService.updateReview(reviewId, customer.id(), request);
    }

    @DeleteMapping("/{reviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @PathVariable UUID reviewId
    ) {
        assertAuthenticated(userSub);
        CustomerSummary customer = customerClient.getByKeycloakId(userSub);
        reviewService.deleteReview(reviewId, customer.id());
    }

    @GetMapping
    public Page<ReviewResponse> listMine(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        assertAuthenticated(userSub);
        CustomerSummary customer = customerClient.getByKeycloakId(userSub);
        return reviewService.listByCustomer(customer.id(), pageable);
    }

    @PostMapping("/{reviewId}/vote")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void vote(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @PathVariable UUID reviewId,
            @Valid @RequestBody VoteRequest request
    ) {
        assertAuthenticated(userSub);
        CustomerSummary customer = customerClient.getByKeycloakId(userSub);
        reviewService.vote(reviewId, customer.id(), request.helpful());
    }

    @PostMapping("/{reviewId}/report")
    @ResponseStatus(HttpStatus.CREATED)
    public void report(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @PathVariable UUID reviewId,
            @Valid @RequestBody CreateReportRequest request
    ) {
        assertAuthenticated(userSub);
        CustomerSummary customer = customerClient.getByKeycloakId(userSub);
        reviewService.createReport(reviewId, customer.id(), request);
    }

    private void assertAuthenticated(String userSub) {
        if (userSub == null || userSub.isBlank()) {
            throw new UnauthorizedException("Authentication required");
        }
    }

    private String buildDisplayName(CustomerSummary customer) {
        String first = customer.firstName() != null ? customer.firstName() : "";
        String last = customer.lastName() != null ? customer.lastName() : "";
        String name = (first + " " + last).trim();
        return name.isEmpty() ? "Customer" : name;
    }
}
