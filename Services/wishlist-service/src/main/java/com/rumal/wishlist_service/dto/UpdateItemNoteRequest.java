package com.rumal.wishlist_service.dto;

import jakarta.validation.constraints.Size;

public record UpdateItemNoteRequest(
        @Size(max = 500) String note
) {
}
