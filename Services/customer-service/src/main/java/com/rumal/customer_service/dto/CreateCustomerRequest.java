package com.rumal.customer_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateCustomerRequest(
        @NotBlank(message = "name is required")
        String name,

        @Email(message = "email must be valid")
        @NotBlank(message = "email is required")
        String email
) {}
