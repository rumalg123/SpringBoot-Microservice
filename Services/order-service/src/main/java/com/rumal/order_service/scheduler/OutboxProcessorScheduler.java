package com.rumal.order_service.scheduler;

import com.rumal.order_service.entity.OutboxEvent;
import com.rumal.order_service.repo.OutboxEventRepository;
import com.rumal.order_service.service.OutboxService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OutboxProcessorScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxProcessorScheduler.class);

    private final OutboxService outboxService;

    @Value("${outbox.processor.batch-size:50}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${outbox.processor.interval-ms:5000}")
    public void processOutboxEvents() {
        List<UUID> eventIds;
        try {
            eventIds = outboxService.claimEventsForProcessing(Instant.now(), batchSize);
        } catch (Exception ex) {
            log.error("Outbox processor claim failed", ex);
            return;
        }
        if (eventIds == null || eventIds.isEmpty()) {
            return;
        }
        for (UUID eventId : eventIds) {
            try {
                outboxService.processEvent(eventId);
            } catch (Exception ex) {
                log.error("Outbox processor failed for event {}", eventId, ex);
            }
        }
    }
}
