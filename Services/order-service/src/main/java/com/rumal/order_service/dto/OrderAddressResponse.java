package com.rumal.order_service.dto;

import java.util.UUID;

public record OrderAddressResponse(
        UUID sourceAddressId,
        String label,
        String recipientName,
        String phone,
        String line1,
        String line2,
        String city,
        String state,
        String postalCode,
        String countryCode
) {
}
