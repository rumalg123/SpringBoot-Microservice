package com.rumal.vendor_service.dto;

import jakarta.validation.constraints.Size;

public record RequestVerificationRequest(
        @Size(max = 500) String verificationDocumentUrl,
        @Size(max = 1000) String notes
) {
}
