package com.rumal.customer_service.dto;

import java.time.Instant;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String name,
        String email,
        Instant createdAt
) {}
