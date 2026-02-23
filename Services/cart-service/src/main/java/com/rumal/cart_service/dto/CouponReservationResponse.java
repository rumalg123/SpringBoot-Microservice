package com.rumal.cart_service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CouponReservationResponse(
        UUID id,
        UUID couponCodeId,
        UUID promotionId,
        String couponCode,
        String status,
        UUID customerId,
        UUID orderId,
        BigDecimal reservedDiscountAmount,
        BigDecimal quotedSubtotal,
        BigDecimal quotedGrandTotal,
        Instant reservedAt,
        Instant expiresAt,
        Instant committedAt,
        Instant releasedAt,
        String releaseReason,
        PromotionQuoteResponse quote
) {
}
