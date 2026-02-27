package com.rumal.order_service.scheduler;

import com.rumal.order_service.entity.OutboxEvent;
import com.rumal.order_service.repo.OutboxEventRepository;
import com.rumal.order_service.service.OutboxService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OutboxProcessorScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxProcessorScheduler.class);

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxService outboxService;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(fixedDelayString = "${outbox.processor.interval-ms:5000}")
    public void processOutboxEvents() {
        List<OutboxEvent> events;
        try {
            events = transactionTemplate.execute(status ->
                    outboxEventRepository.findPendingEventsForProcessing(Instant.now(), 50));
        } catch (Exception ex) {
            log.error("Outbox processor query failed", ex);
            return;
        }
        if (events == null || events.isEmpty()) {
            return;
        }
        for (OutboxEvent event : events) {
            try {
                outboxService.processEvent(event);
            } catch (Exception ex) {
                log.error("Outbox processor failed for event {}", event.getId(), ex);
            }
        }
    }
}
