package com.rumal.payment_service.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record InitiatePaymentRequest(
        @NotNull UUID orderId
) {}
