package com.rumal.order_service.scheduler;

import com.rumal.order_service.service.OrderExportService;
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
public class OrderExportJobScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderExportJobScheduler.class);

    private final OrderExportService orderExportService;

    @Value("${order.export.processor.batch-size:2}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${order.export.processor.interval-ms:5000}")
    public void processPendingJobs() {
        Instant now = Instant.now();
        List<UUID> jobIds;
        try {
            jobIds = orderExportService.claimJobsForProcessing(now, batchSize);
        } catch (Exception ex) {
            log.error("Failed claiming order export jobs", ex);
            return;
        }
        for (UUID jobId : jobIds) {
            try {
                orderExportService.processJob(jobId);
            } catch (Exception ex) {
                log.error("Failed processing order export job {}", jobId, ex);
            }
        }
    }

    @Scheduled(fixedDelayString = "${order.export.cleanup.interval-ms:3600000}")
    public void cleanupExpiredJobs() {
        try {
            orderExportService.expireReadyJobs(Instant.now());
        } catch (Exception ex) {
            log.warn("Failed cleaning up expired order export jobs", ex);
        }
    }
}
