package com.rumal.order_service.dto;

import java.util.UUID;

public record CustomerProductPurchaseCheckResponse(
        boolean purchased,
        UUID orderId,
        UUID vendorId
) {}
