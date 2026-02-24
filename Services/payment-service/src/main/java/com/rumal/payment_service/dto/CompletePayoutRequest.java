package com.rumal.payment_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompletePayoutRequest(
        @NotBlank @Size(max = 200) String referenceNumber,
        @Size(max = 1000) String adminNote
) {}
