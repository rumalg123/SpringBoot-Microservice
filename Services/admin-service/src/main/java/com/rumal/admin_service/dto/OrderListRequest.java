package com.rumal.admin_service.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderListRequest(
        UUID customerId,
        String customerEmail,
        UUID vendorId,
        String status,
        Instant createdAfter,
        Instant createdBefore,
        int page,
        int size,
        List<String> sort
) {
}
