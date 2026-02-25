package com.rumal.review_service.dto;

import jakarta.validation.constraints.NotNull;

public record VoteRequest(
        @NotNull(message = "helpful is required")
        Boolean helpful
) {}
