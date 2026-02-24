package com.rumal.payment_service.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreatePayoutRequest(
        @NotNull UUID vendorId,
        @NotNull UUID bankAccountId,
        @NotEmpty List<UUID> vendorOrderIds,
        @Size(max = 1000) String adminNote
) {}
