package com.rumal.admin_service.scheduler;

import com.rumal.admin_service.service.AdminAuditService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminAuditOutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(AdminAuditOutboxProcessor.class);

    private final AdminAuditService adminAuditService;

    @Value("${admin.audit.outbox.batch-size:50}")
    private int batchSize;

    @Scheduled(
            fixedDelayString = "${admin.audit.outbox.poll-interval-ms:5000}",
            initialDelayString = "${admin.audit.outbox.initial-delay-ms:10000}"
    )
    public void process() {
        try {
            adminAuditService.processOutboxBatch(batchSize);
        } catch (Exception ex) {
            log.error("Admin audit outbox batch failed", ex);
        }
    }
}
