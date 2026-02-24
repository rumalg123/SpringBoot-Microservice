package com.rumal.order_service.dto;

import jakarta.validation.constraints.Size;

public record UpdateOrderNoteRequest(
        @Size(max = 1000) String adminNote
) {}
