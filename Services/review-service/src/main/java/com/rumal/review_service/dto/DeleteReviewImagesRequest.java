package com.rumal.review_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record DeleteReviewImagesRequest(
        @Size(max = 5, message = "Cannot delete more than 5 images at a time")
        List<@NotBlank(message = "image key cannot be blank") String> keys
) {
}
