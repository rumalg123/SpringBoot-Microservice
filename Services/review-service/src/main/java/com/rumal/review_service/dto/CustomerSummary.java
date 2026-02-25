package com.rumal.review_service.dto;

import java.util.UUID;

public record CustomerSummary(
        UUID id,
        String firstName,
        String lastName
) {}
