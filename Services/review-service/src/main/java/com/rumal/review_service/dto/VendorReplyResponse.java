package com.rumal.review_service.dto;

import java.time.Instant;
import java.util.UUID;

public record VendorReplyResponse(
        UUID id,
        UUID vendorId,
        String comment,
        Instant createdAt,
        Instant updatedAt
) {}
