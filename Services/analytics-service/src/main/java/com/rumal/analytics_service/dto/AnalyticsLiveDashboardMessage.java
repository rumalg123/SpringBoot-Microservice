package com.rumal.analytics_service.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record AnalyticsLiveDashboardMessage(
        UUID orderId,
        Set<UUID> vendorIds,
        String trigger,
        Instant occurredAt
) {
}
