package com.rumal.customer_service.dto;

import java.util.UUID;

public record InternalCustomerSummary(
        UUID id,
        String firstName,
        String lastName
) {

    public static InternalCustomerSummary fromFullName(UUID id, String name) {
        if (name == null || name.isBlank()) {
            return new InternalCustomerSummary(id, null, null);
        }
        String trimmed = name.trim();
        int spaceIndex = trimmed.indexOf(' ');
        if (spaceIndex < 0) {
            return new InternalCustomerSummary(id, trimmed, null);
        }
        return new InternalCustomerSummary(
                id,
                trimmed.substring(0, spaceIndex),
                trimmed.substring(spaceIndex + 1).trim()
        );
    }
}
