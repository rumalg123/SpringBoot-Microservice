package com.rumal.cart_service.dto;

import java.util.UUID;

public record CustomerSummary(
        UUID id,
        String name,
        String email
) {
}
