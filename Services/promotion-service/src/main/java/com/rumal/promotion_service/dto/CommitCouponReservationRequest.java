package com.rumal.promotion_service.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CommitCouponReservationRequest(
        @NotNull UUID orderId
) {
}
