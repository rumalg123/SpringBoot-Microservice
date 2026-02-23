package com.rumal.promotion_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReleaseCouponReservationRequest(
        @NotBlank @Size(max = 500) String reason
) {
}
