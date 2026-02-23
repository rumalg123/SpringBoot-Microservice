package com.rumal.vendor_service.dto;

import jakarta.validation.constraints.Size;

public record VendorLifecycleActionRequest(
        @Size(max = 500) String reason
) {
}

