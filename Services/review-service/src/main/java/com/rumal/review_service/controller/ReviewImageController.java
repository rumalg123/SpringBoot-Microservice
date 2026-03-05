package com.rumal.review_service.controller;

import com.rumal.review_service.dto.ReviewImageUploadResponse;
import com.rumal.review_service.exception.UnauthorizedException;
import com.rumal.review_service.security.InternalRequestVerifier;
import com.rumal.review_service.service.ReviewImageStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/reviews/me/images")
@RequiredArgsConstructor
public class ReviewImageController {

    private final ReviewImageStorageService reviewImageStorageService;
    private final InternalRequestVerifier internalRequestVerifier;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewImageUploadResponse uploadImages(
            @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestHeader(value = "X-User-Email-Verified", required = false) String emailVerified,
            @RequestParam("files") List<MultipartFile> files
    ) {
        internalRequestVerifier.verify(internalAuth);
        verifyEmailVerified(emailVerified);
        if (userSub == null || userSub.isBlank()) {
            throw new UnauthorizedException("Authentication required");
        }
        List<String> keys = reviewImageStorageService.uploadImages(files);
        return new ReviewImageUploadResponse(keys);
    }

    private void verifyEmailVerified(String emailVerified) {
        if (emailVerified == null || !"true".equalsIgnoreCase(emailVerified.trim())) {
            throw new UnauthorizedException("Email is not verified");
        }
    }
}
