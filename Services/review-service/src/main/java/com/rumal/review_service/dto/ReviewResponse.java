package com.rumal.review_service.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReviewResponse(
        UUID id,
        UUID customerId,
        String customerDisplayName,
        UUID productId,
        UUID vendorId,
        UUID orderId,
        int rating,
        String title,
        String comment,
        List<String> images,
        int helpfulCount,
        int notHelpfulCount,
        boolean verifiedPurchase,
        boolean active,
        VendorReplyResponse vendorReply,
        Instant createdAt,
        Instant updatedAt
) {}
