package com.rumal.customer_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerAddressRequest(
        @Size(max = 50, message = "label must be at most 50 characters")
        String label,

        @NotBlank(message = "recipientName is required")
        @Size(max = 120, message = "recipientName must be at most 120 characters")
        String recipientName,

        @NotBlank(message = "phone is required")
        @Pattern(regexp = "^[+0-9()\\-\\s]{7,20}$", message = "phone format is invalid")
        String phone,

        @NotBlank(message = "line1 is required")
        @Size(max = 180, message = "line1 must be at most 180 characters")
        String line1,

        @Size(max = 180, message = "line2 must be at most 180 characters")
        String line2,

        @NotBlank(message = "city is required")
        @Size(max = 80, message = "city must be at most 80 characters")
        String city,

        @NotBlank(message = "state is required")
        @Size(max = 80, message = "state must be at most 80 characters")
        String state,

        @NotBlank(message = "postalCode is required")
        @Size(max = 30, message = "postalCode must be at most 30 characters")
        String postalCode,

        @NotBlank(message = "countryCode is required")
        @Pattern(regexp = "^[A-Za-z]{2}$", message = "countryCode must be a 2-letter code")
        String countryCode,

        Boolean defaultShipping,
        Boolean defaultBilling
) {
}
