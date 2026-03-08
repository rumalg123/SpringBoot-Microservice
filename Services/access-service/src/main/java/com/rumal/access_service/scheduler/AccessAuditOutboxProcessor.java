package com.rumal.access_service.scheduler;

import com.rumal.access_service.service.AccessServiceImpl;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccessAuditOutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(AccessAuditOutboxProcessor.class);

    private final AccessServiceImpl accessService;

    @Value("${access.audit.outbox.batch-size:50}")
    private int batchSize;

    @Scheduled(
            fixedDelayString = "${access.audit.outbox.poll-interval-ms:5000}",
            initialDelayString = "${access.audit.outbox.initial-delay-ms:10000}"
    )
    public void process() {
        try {
            accessService.processAuditOutboxBatch(batchSize);
        } catch (Exception ex) {
            log.error("Access audit outbox batch failed", ex);
        }
    }
}
