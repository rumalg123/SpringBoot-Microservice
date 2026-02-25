package com.rumal.review_service.dto;

import com.rumal.review_service.entity.ReportStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateReportStatusRequest(
        @NotNull(message = "status is required")
        ReportStatus status,

        @Size(max = 500, message = "adminNotes must be at most 500 characters")
        String adminNotes
) {}
