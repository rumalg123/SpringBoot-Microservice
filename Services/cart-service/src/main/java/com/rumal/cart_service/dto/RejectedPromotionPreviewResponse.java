package com.rumal.cart_service.dto;

import java.util.UUID;

public record RejectedPromotionPreviewResponse(
        UUID promotionId,
        String promotionName,
        String reason
) {
}
