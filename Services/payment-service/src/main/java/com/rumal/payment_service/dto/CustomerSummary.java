package com.rumal.payment_service.dto;

import java.util.UUID;

public record CustomerSummary(
        UUID id,
        String name,
        String email,
        String phone
) {}
