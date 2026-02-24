package com.rumal.customer_service.dto;

import java.time.Instant;
import java.util.UUID;

public record CommunicationPreferencesResponse(
        UUID id,
        UUID customerId,
        boolean emailMarketing,
        boolean smsMarketing,
        boolean pushNotifications,
        boolean orderUpdates,
        boolean promotionalAlerts,
        Instant createdAt,
        Instant updatedAt
) {
}
