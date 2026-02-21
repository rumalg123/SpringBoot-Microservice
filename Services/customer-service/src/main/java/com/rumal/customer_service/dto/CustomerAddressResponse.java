package com.rumal.customer_service.dto;

import java.time.Instant;
import java.util.UUID;

public record CustomerAddressResponse(
        UUID id,
        UUID customerId,
        String label,
        String recipientName,
        String phone,
        String line1,
        String line2,
        String city,
        String state,
        String postalCode,
        String countryCode,
        boolean defaultShipping,
        boolean defaultBilling,
        boolean deleted,
        Instant createdAt,
        Instant updatedAt
) {
}
