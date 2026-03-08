package com.rumal.analytics_service.dto;

import java.time.Instant;
import java.util.UUID;

public record AnalyticsLiveRefreshEvent(
        String scope,
        UUID vendorId,
        String trigger,
        Instant occurredAt
) {
}
