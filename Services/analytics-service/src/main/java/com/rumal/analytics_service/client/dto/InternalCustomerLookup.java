package com.rumal.analytics_service.client.dto;

import java.util.UUID;

public record InternalCustomerLookup(
        UUID id,
        String firstName,
        String lastName
) {
}
