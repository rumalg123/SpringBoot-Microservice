package com.rumal.order_service.dto;

import java.util.UUID;

public record CommitCouponReservationRequest(
        UUID orderId
) {
}
