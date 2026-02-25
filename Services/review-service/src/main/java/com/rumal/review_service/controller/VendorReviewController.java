package com.rumal.review_service.controller;

import com.rumal.review_service.client.VendorClient;
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
@RequestMapping("/reviews/vendor")
@RequiredArgsConstructor
public class VendorReviewController {

    private final ReviewService reviewService;
    private final VendorClient vendorClient;

    @GetMapping
    public Page<ReviewResponse> listForVendor(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        UUID vendorId = resolveVendorId(userSub);
        return reviewService.listByVendor(vendorId, pageable);
    }

    @PostMapping("/{reviewId}/reply")
    @ResponseStatus(HttpStatus.CREATED)
    public VendorReplyResponse reply(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @PathVariable UUID reviewId,
            @Valid @RequestBody CreateVendorReplyRequest request
    ) {
        UUID vendorId = resolveVendorId(userSub);
        return reviewService.createVendorReply(reviewId, vendorId, request);
    }

    @PutMapping("/replies/{replyId}")
    public VendorReplyResponse updateReply(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @PathVariable UUID replyId,
            @Valid @RequestBody UpdateVendorReplyRequest request
    ) {
        UUID vendorId = resolveVendorId(userSub);
        return reviewService.updateVendorReply(replyId, vendorId, request);
    }

    @DeleteMapping("/replies/{replyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteReply(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @PathVariable UUID replyId
    ) {
        UUID vendorId = resolveVendorId(userSub);
        reviewService.deleteVendorReply(replyId, vendorId);
    }

    private UUID resolveVendorId(String userSub) {
        if (userSub == null || userSub.isBlank()) {
            throw new UnauthorizedException("Authentication required");
        }
        return vendorClient.getVendorIdByKeycloakSub(userSub);
    }
}
