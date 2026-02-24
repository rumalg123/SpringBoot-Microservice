package com.rumal.customer_service.dto;

import com.rumal.customer_service.entity.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateCustomerProfileRequest(
        @NotBlank(message = "firstName is required")
        @Size(max = 60, message = "firstName must be at most 60 characters")
        String firstName,

        @NotBlank(message = "lastName is required")
        @Size(max = 60, message = "lastName must be at most 60 characters")
        String lastName,

        @Size(max = 40, message = "phone must be at most 40 characters")
        String phone,

        @Size(max = 260, message = "avatarUrl must be at most 260 characters")
        String avatarUrl,

        LocalDate dateOfBirth,

        Gender gender
) {
}
