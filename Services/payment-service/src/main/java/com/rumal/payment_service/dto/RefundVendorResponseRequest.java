package com.rumal.payment_service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RefundVendorResponseRequest(
        @NotNull Boolean approved,
        @Size(max = 1000) String note
) {}
