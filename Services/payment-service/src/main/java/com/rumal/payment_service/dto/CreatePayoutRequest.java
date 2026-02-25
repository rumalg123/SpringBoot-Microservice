package com.rumal.payment_service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreatePayoutRequest(
        @NotNull UUID vendorId,
        @NotNull UUID bankAccountId,
        @NotEmpty List<UUID> vendorOrderIds,
        @NotNull @DecimalMin("0.01") BigDecimal payoutAmount,
        @NotNull @DecimalMin("0.00") BigDecimal platformFee,
        @Size(max = 1000) String adminNote
) {}
