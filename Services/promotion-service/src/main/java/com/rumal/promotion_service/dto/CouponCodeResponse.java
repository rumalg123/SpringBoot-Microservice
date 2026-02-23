package com.rumal.promotion_service.dto;

import java.time.Instant;
import java.util.UUID;

public record CouponCodeResponse(
        UUID id,
        UUID promotionId,
        String code,
        boolean active,
        Integer maxUses,
        Integer maxUsesPerCustomer,
        Integer reservationTtlSeconds,
        Instant startsAt,
        Instant endsAt,
        String createdByUserSub,
        String updatedByUserSub,
        Instant createdAt,
        Instant updatedAt
) {
}
