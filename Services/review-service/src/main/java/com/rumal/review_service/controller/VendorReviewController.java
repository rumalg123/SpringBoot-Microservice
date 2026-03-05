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
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
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
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String emailVerified,
            @RequestParam(required = false) UUID vendorId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        UUID resolvedVendorId = resolveVendorId(userSub, userRoles, emailVerified, vendorId);
        return reviewService.listByVendor(resolvedVendorId, pageable);
    }

    @PostMapping("/{reviewId}/reply")
    @ResponseStatus(HttpStatus.CREATED)
    public VendorReplyResponse reply(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String emailVerified,
            @RequestParam(required = false) UUID vendorId,
            @PathVariable UUID reviewId,
            @Valid @RequestBody CreateVendorReplyRequest request
    ) {
        UUID resolvedVendorId = resolveVendorId(userSub, userRoles, emailVerified, vendorId);
        return reviewService.createVendorReply(reviewId, resolvedVendorId, request);
    }

    @PutMapping("/replies/{replyId}")
    public VendorReplyResponse updateReply(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String emailVerified,
            @RequestParam(required = false) UUID vendorId,
            @PathVariable UUID replyId,
            @Valid @RequestBody UpdateVendorReplyRequest request
    ) {
        UUID resolvedVendorId = resolveVendorId(userSub, userRoles, emailVerified, vendorId);
        return reviewService.updateVendorReply(replyId, resolvedVendorId, request);
    }

    @DeleteMapping("/replies/{replyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteReply(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String emailVerified,
            @RequestParam(required = false) UUID vendorId,
            @PathVariable UUID replyId
    ) {
        UUID resolvedVendorId = resolveVendorId(userSub, userRoles, emailVerified, vendorId);
        reviewService.deleteVendorReply(replyId, resolvedVendorId);
    }

    private UUID resolveVendorId(String userSub, String userRoles, String emailVerified, UUID vendorIdHint) {
        String normalizedUserSub = requireUserSub(userSub);
        verifyEmailVerified(emailVerified);
        assertVendorRole(userRoles);
        return vendorClient.getVendorIdByKeycloakSub(normalizedUserSub, vendorIdHint);
    }

    private String requireUserSub(String userSub) {
        if (!StringUtils.hasText(userSub)) {
            throw new UnauthorizedException("Authentication required");
        }
        return userSub.trim();
    }

    private void verifyEmailVerified(String emailVerified) {
        if (!StringUtils.hasText(emailVerified) || !"true".equalsIgnoreCase(emailVerified.trim())) {
            throw new UnauthorizedException("Email is not verified");
        }
    }

    private void assertVendorRole(String userRoles) {
        Set<String> roles = parseRoles(userRoles);
        if (roles.contains("vendor_admin") || roles.contains("vendor_staff")) {
            return;
        }
        throw new UnauthorizedException("Vendor role required");
    }

    private Set<String> parseRoles(String userRoles) {
        if (!StringUtils.hasText(userRoles)) {
            return Set.of();
        }
        Set<String> roles = new LinkedHashSet<>();
        for (String role : userRoles.split(",")) {
            if (!StringUtils.hasText(role)) {
                continue;
            }
            String normalized = role.trim().toLowerCase(Locale.ROOT);
            if (normalized.startsWith("role_")) {
                normalized = normalized.substring("role_".length());
            } else if (normalized.startsWith("role-")) {
                normalized = normalized.substring("role-".length());
            } else if (normalized.startsWith("role:")) {
                normalized = normalized.substring("role:".length());
            }
            normalized = normalized.replace('-', '_').replace(' ', '_');
            if (!normalized.isEmpty()) {
                roles.add(normalized);
            }
        }
        return Set.copyOf(roles);
    }
}
