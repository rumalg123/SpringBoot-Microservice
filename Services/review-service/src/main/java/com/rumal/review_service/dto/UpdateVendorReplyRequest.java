package com.rumal.review_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateVendorReplyRequest(
        @NotBlank(message = "comment is required")
        @Size(max = 1000, message = "comment must be at most 1000 characters")
        String comment
) {}
