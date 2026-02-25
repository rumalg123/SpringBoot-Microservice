package com.rumal.review_service.controller;

import com.rumal.review_service.dto.ReviewImageUploadResponse;
import com.rumal.review_service.exception.UnauthorizedException;
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

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewImageUploadResponse uploadImages(
            @RequestHeader(value = "X-User-Sub", required = false) String userSub,
            @RequestParam("files") List<MultipartFile> files
    ) {
        if (userSub == null || userSub.isBlank()) {
            throw new UnauthorizedException("Authentication required");
        }
        List<String> keys = reviewImageStorageService.uploadImages(files);
        return new ReviewImageUploadResponse(keys);
    }
}
