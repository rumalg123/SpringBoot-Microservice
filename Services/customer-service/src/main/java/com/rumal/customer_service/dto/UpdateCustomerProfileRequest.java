package com.rumal.customer_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCustomerProfileRequest(
        @NotBlank(message = "firstName is required")
        @Size(max = 60, message = "firstName must be at most 60 characters")
        String firstName,

        @NotBlank(message = "lastName is required")
        @Size(max = 60, message = "lastName must be at most 60 characters")
        String lastName
) {
}
