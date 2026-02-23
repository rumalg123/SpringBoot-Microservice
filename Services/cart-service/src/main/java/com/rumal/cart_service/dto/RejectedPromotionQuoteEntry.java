package com.rumal.cart_service.dto;

import java.util.UUID;

public record RejectedPromotionQuoteEntry(
        UUID promotionId,
        String promotionName,
        String reason
) {
}
