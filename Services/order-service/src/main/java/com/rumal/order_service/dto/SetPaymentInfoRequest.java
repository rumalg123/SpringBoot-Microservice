package com.rumal.order_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SetPaymentInfoRequest(
        @NotBlank @Size(max = 120) String paymentId,
        @Size(max = 50) String paymentMethod,
        @Size(max = 200) String paymentGatewayRef
) {}
