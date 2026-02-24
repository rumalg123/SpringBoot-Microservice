package com.rumal.customer_service.dto;

import jakarta.validation.constraints.Size;

public record RegisterIdentityCustomerRequest(
        @Size(max = 120, message = "name must be at most 120 characters")
        String name
) {
}
