package com.rumal.customer_service.dto;

import jakarta.validation.constraints.Min;

public record AddLoyaltyPointsRequest(
        @Min(value = 1, message = "points must be at least 1")
        int points
) {
}
