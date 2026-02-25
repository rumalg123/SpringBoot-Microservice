package com.rumal.analytics_service.client.dto;

import java.util.UUID;

public record ProductSoldEntry(
        UUID id,
        String name,
        UUID vendorId,
        long soldCount
) {}
