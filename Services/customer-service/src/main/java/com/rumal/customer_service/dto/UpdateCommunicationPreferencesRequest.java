package com.rumal.customer_service.dto;

public record UpdateCommunicationPreferencesRequest(
        Boolean emailMarketing,
        Boolean smsMarketing,
        Boolean pushNotifications,
        Boolean orderUpdates,
        Boolean promotionalAlerts
) {
}
