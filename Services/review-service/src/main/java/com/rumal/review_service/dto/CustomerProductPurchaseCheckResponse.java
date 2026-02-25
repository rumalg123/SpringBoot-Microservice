package com.rumal.review_service.dto;

import java.util.UUID;

public record CustomerProductPurchaseCheckResponse(
        boolean purchased,
        UUID orderId
) {}
