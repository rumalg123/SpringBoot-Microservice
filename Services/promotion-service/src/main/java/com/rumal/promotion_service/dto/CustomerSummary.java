package com.rumal.promotion_service.dto;

import java.util.UUID;

public record CustomerSummary(
        UUID id,
        String name,
        String email
) {
}
