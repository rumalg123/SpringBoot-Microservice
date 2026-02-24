package com.rumal.customer_service.dto;

import java.util.List;
import java.util.UUID;

public record LinkedAccountsResponse(
        UUID customerId,
        List<String> providers
) {
}
