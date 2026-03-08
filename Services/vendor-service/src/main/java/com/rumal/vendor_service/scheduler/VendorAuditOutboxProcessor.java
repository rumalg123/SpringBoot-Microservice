package com.rumal.vendor_service.scheduler;

import com.rumal.vendor_service.service.VendorServiceImpl;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VendorAuditOutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(VendorAuditOutboxProcessor.class);

    private final VendorServiceImpl vendorService;

    @Value("${vendor.audit.outbox.batch-size:50}")
    private int batchSize;

    @Scheduled(
            fixedDelayString = "${vendor.audit.outbox.poll-interval-ms:5000}",
            initialDelayString = "${vendor.audit.outbox.initial-delay-ms:10000}"
    )
    public void process() {
        try {
            vendorService.processAuditOutboxBatch(batchSize);
        } catch (Exception ex) {
            log.error("Vendor audit outbox batch failed", ex);
        }
    }
}
