package com.rumal.review_service.dto;

import com.rumal.review_service.entity.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReportRequest(
        @NotNull(message = "reason is required")
        ReportReason reason,

        @Size(max = 500, message = "description must be at most 500 characters")
        String description
) {}
