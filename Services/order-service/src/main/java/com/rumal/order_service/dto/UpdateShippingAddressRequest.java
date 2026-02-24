package com.rumal.order_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateShippingAddressRequest(
        @Size(max = 50) String label,
        @NotBlank @Size(max = 120) String recipientName,
        @NotBlank @Size(max = 32) String phone,
        @NotBlank @Size(max = 180) String line1,
        @Size(max = 180) String line2,
        @NotBlank @Size(max = 80) String city,
        @NotBlank @Size(max = 80) String state,
        @NotBlank @Size(max = 30) String postalCode,
        @NotBlank @Size(min = 2, max = 2) String countryCode
) {}
