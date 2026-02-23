package com.rumal.promotion_service.dto;

import java.util.UUID;

public record RejectedPromotionQuoteEntry(
        UUID promotionId,
        String promotionName,
        String reason
) {
}
