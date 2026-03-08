package com.rumal.order_service.dto;

import java.time.Instant;
import java.util.UUID;

public record OrderExportJobRequest(
        String format,
        String status,
        String customerEmail,
        Instant createdAfter,
        Instant createdBefore,
        UUID vendorId,
        String requestedBy,
        String requestedRoles
) {
}
