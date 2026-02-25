package com.rumal.review_service.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public record UpdateReviewRequest(
        @NotNull(message = "rating is required")
        @Min(value = 1, message = "rating must be at least 1")
        @Max(value = 5, message = "rating must be at most 5")
        Integer rating,

        @Size(max = 150, message = "title must be at most 150 characters")
        String title,

        @NotBlank(message = "comment is required")
        @Size(max = 2000, message = "comment must be at most 2000 characters")
        String comment,

        @Size(max = 5, message = "images cannot contain more than 5 items")
        List<@NotBlank(message = "image key cannot be blank") String> images
) {}
