package com.rumal.admin_service.dto;

import java.time.Instant;
import java.util.UUID;

public record OrderExportJobResponse(
        UUID jobId,
        String status,
        String format,
        String fileName,
        String contentType,
        String customerEmail,
        String filterStatus,
        UUID vendorId,
        String requestedBy,
        Integer rowCount,
        Long fileSizeBytes,
        String failureMessage,
        Instant createdAfter,
        Instant createdBefore,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        Instant expiresAt
) {
}
