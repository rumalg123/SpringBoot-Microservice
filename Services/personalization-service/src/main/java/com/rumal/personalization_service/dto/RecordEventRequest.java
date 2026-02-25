package com.rumal.personalization_service.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record RecordEventRequest(
        @NotNull EventType eventType,
        @NotNull UUID productId,
        String categorySlugs,
        UUID vendorId,
        String brandName,
        BigDecimal price,
        String metadata
) {}
