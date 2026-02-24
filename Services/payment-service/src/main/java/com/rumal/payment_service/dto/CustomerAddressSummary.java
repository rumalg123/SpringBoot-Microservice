package com.rumal.payment_service.dto;

import java.util.UUID;

public record CustomerAddressSummary(
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
        boolean deleted
) {}
