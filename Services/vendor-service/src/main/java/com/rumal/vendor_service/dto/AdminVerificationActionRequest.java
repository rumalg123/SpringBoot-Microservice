package com.rumal.vendor_service.dto;

import jakarta.validation.constraints.Size;

public record AdminVerificationActionRequest(
        @Size(max = 1000) String notes
) {
}
