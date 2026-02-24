package com.rumal.order_service.dto;

import jakarta.validation.constraints.Size;

public record CancelOrderRequest(
        @Size(max = 240) String reason
) {}
