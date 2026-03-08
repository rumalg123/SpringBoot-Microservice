package com.rumal.admin_service.dto;

import java.time.Instant;
import java.util.UUID;

public record CreateOrderExportRequest(
        String format,
        String status,
        String customerEmail,
        Instant createdAfter,
        Instant createdBefore,
        UUID vendorId
) {
}
