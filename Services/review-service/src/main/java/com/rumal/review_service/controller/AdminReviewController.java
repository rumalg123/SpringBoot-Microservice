package com.rumal.review_service.controller;

import com.rumal.review_service.dto.*;
import com.rumal.review_service.entity.ReportStatus;
import com.rumal.review_service.entity.ReviewReport;
import com.rumal.review_service.security.InternalRequestVerifier;
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
@RequestMapping("/admin/reviews")
@RequiredArgsConstructor
public class AdminReviewController {

    private final ReviewService reviewService;
    private final InternalRequestVerifier internalRequestVerifier;

    @GetMapping
    public Page<ReviewResponse> list(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID vendorId,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        internalRequestVerifier.verify(internalAuth);
        return reviewService.adminList(pageable, productId, vendorId, customerId, rating, active);
    }

    @PutMapping("/{reviewId}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID reviewId
    ) {
        internalRequestVerifier.verify(internalAuth);
        reviewService.adminDeactivate(reviewId);
    }

    @PutMapping("/{reviewId}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void activate(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID reviewId
    ) {
        internalRequestVerifier.verify(internalAuth);
        reviewService.adminActivate(reviewId);
    }

    @DeleteMapping("/{reviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID reviewId
    ) {
        internalRequestVerifier.verify(internalAuth);
        reviewService.adminDelete(reviewId);
    }

    @GetMapping("/reports")
    public Page<ReviewReport> listReports(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestParam(required = false) ReportStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        internalRequestVerifier.verify(internalAuth);
        return reviewService.listReports(pageable, status);
    }

    @PutMapping("/reports/{reportId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateReportStatus(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @PathVariable UUID reportId,
            @Valid @RequestBody UpdateReportStatusRequest request
    ) {
        internalRequestVerifier.verify(internalAuth);
        reviewService.updateReportStatus(reportId, request);
    }
}
