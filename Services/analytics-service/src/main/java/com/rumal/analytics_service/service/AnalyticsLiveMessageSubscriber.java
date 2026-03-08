package com.rumal.analytics_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rumal.analytics_service.dto.AnalyticsLiveDashboardMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class AnalyticsLiveMessageSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsLiveMessageSubscriber.class);

    private final ObjectMapper objectMapper;
    private final AnalyticsLiveStreamService analyticsLiveStreamService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        if (message == null || message.getBody() == null || message.getBody().length == 0) {
            return;
        }

        try {
            AnalyticsLiveDashboardMessage payload = objectMapper.readValue(message.getBody(), AnalyticsLiveDashboardMessage.class);
            analyticsLiveStreamService.publishRefresh(payload);
        } catch (Exception ex) {
            String rawPayload = new String(message.getBody(), StandardCharsets.UTF_8);
            log.warn("Failed to process analytics live Redis message: {}", rawPayload, ex);
        }
    }
}
