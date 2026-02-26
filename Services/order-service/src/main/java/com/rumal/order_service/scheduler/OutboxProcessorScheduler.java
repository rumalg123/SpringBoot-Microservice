package com.rumal.order_service.scheduler;

import com.rumal.order_service.entity.OutboxEvent;
import com.rumal.order_service.entity.OutboxEventStatus;
import com.rumal.order_service.repo.OutboxEventRepository;
import com.rumal.order_service.service.OutboxService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OutboxProcessorScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxProcessorScheduler.class);

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxService outboxService;

    @Scheduled(fixedDelayString = "${outbox.processor.interval-ms:5000}")
    public void processOutboxEvents() {
        try {
            List<OutboxEvent> events = outboxEventRepository
                    .findByStatusAndNextRetryAtBeforeOrStatusAndNextRetryAtIsNull(
                            OutboxEventStatus.PENDING, Instant.now(),
                            OutboxEventStatus.PENDING,
                            PageRequest.of(0, 50)
                    );
            for (OutboxEvent event : events) {
                outboxService.processEvent(event);
            }
        } catch (Exception ex) {
            log.error("Outbox processor failed", ex);
        }
    }
}
