package com.rumal.review_service.controller;

import com.rumal.review_service.dto.*;
import com.rumal.review_service.exception.UnauthorizedException;
import com.rumal.review_service.security.InternalRequestVerifier;
import com.rumal.review_service.service.ReviewService;
import com.rumal.review_service.service.VendorReviewAccessScopeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/reviews/vendor")
@RequiredArgsConstructor
public class VendorReviewController {

    private final ReviewService reviewService;
    private final InternalRequestVerifier internalRequestVerifier;
    private final VendorReviewAccessScopeService vendorReviewAccessScopeService;

    @GetMapping
    public Page<ReviewResponse> listForVendor(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String emailVerified,
            @RequestParam(required = false) UUID vendorId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        UUID resolvedVendorId = resolveVendorId(internalAuth, userSub, userRoles, emailVerified, vendorId);
        return reviewService.listByVendor(resolvedVendorId, pageable);
    }

    @PostMapping("/{reviewId}/reply")
    @ResponseStatus(HttpStatus.CREATED)
    public VendorReplyResponse reply(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String emailVerified,
            @RequestParam(required = false) UUID vendorId,
            @PathVariable UUID reviewId,
            @Valid @RequestBody CreateVendorReplyRequest request
    ) {
        UUID resolvedVendorId = resolveVendorId(internalAuth, userSub, userRoles, emailVerified, vendorId);
        return reviewService.createVendorReply(reviewId, resolvedVendorId, request);
    }

    @PutMapping("/replies/{replyId}")
    public VendorReplyResponse updateReply(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String emailVerified,
            @RequestParam(required = false) UUID vendorId,
            @PathVariable UUID replyId,
            @Valid @RequestBody UpdateVendorReplyRequest request
    ) {
        UUID resolvedVendorId = resolveVendorId(internalAuth, userSub, userRoles, emailVerified, vendorId);
        return reviewService.updateVendorReply(replyId, resolvedVendorId, request);
    }

    @DeleteMapping("/replies/{replyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteReply(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String emailVerified,
            @RequestParam(required = false) UUID vendorId,
            @PathVariable UUID replyId
    ) {
        UUID resolvedVendorId = resolveVendorId(internalAuth, userSub, userRoles, emailVerified, vendorId);
        reviewService.deleteVendorReply(replyId, resolvedVendorId);
    }

    private UUID resolveVendorId(String internalAuth, String userSub, String userRoles, String emailVerified, UUID vendorIdHint) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(emailVerified);
        return vendorReviewAccessScopeService.resolveVendorIdForReviewManage(userSub, userRoles, internalAuth, vendorIdHint);
    }

    private void verifyEmailVerified(String emailVerified) {
        if (!StringUtils.hasText(emailVerified) || !"true".equalsIgnoreCase(emailVerified.trim())) {
            throw new UnauthorizedException("Email is not verified");
        }
    }
}
