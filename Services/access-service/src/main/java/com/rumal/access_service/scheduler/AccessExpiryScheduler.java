package com.rumal.access_service.scheduler;

import com.rumal.access_service.service.AccessExpiryProcessor;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccessExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(AccessExpiryScheduler.class);
    private final AccessExpiryProcessor accessExpiryProcessor;

    @Scheduled(fixedDelayString = "${access.expiry.check-interval-ms:60000}")
    public void deactivateExpired() {
        int total = 0;
        total += runSafely("platform staff", accessExpiryProcessor::deactivateExpiredPlatformStaff);
        total += runSafely("vendor staff", accessExpiryProcessor::deactivateExpiredVendorStaff);
        total += runSafely("API keys", accessExpiryProcessor::deactivateExpiredApiKeys);
        if (total > 0) {
            log.info("Scheduled expiry check deactivated {} records total", total);
        }
    }

    private int runSafely(String label, java.util.function.IntSupplier task) {
        try {
            return task.getAsInt();
        } catch (Exception ex) {
            log.error("Error during scheduled {} expiry check", label, ex);
            return 0;
        }
    }
}
