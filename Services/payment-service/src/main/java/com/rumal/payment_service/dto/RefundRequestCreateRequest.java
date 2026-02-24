package com.rumal.payment_service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record RefundRequestCreateRequest(
        @NotNull UUID orderId,
        @NotNull UUID vendorOrderId,
        @NotNull @DecimalMin("0.01") BigDecimal refundAmount,
        @NotBlank @Size(max = 1000) String reason
) {}
