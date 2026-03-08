package com.rumal.order_service.dto;

import java.util.UUID;

public record CustomerPromotionEligibilityResponse(
        UUID customerId,
        long qualifyingOrderCount,
        boolean hasQualifyingOrders
) {
}
