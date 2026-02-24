package com.rumal.promotion_service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CouponUsageResponse(
        UUID reservationId,
        String couponCode,
        String promotionName,
        BigDecimal discountAmount,
        UUID orderId,
        Instant committedAt
) {
}
