package com.rumal.cart_service.dto;

import jakarta.validation.constraints.Size;

public record UpdateCartNoteRequest(
        @Size(max = 500) String note
) {
}
