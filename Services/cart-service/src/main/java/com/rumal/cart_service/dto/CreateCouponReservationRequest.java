package com.rumal.cart_service.dto;

import java.util.UUID;

public record CreateCouponReservationRequest(
        String couponCode,
        UUID customerId,
        PromotionQuoteRequest quoteRequest,
        String requestKey
) {
}
