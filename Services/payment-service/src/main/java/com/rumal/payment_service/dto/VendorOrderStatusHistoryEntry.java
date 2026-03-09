package com.rumal.payment_service.dto;

import java.time.Instant;
import java.util.UUID;

public record VendorOrderStatusHistoryEntry(
        UUID id,
        String fromStatus,
        String toStatus,
        Instant createdAt
) {}
