package com.rumal.access_service.scheduler;

import com.rumal.access_service.service.AccessService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccessExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(AccessExpiryScheduler.class);
    private final AccessService accessService;

    @Scheduled(fixedDelayString = "${access.expiry.check-interval-ms:60000}")
    public void deactivateExpired() {
        try {
            int count = accessService.deactivateExpiredAccess();
            if (count > 0) {
                log.info("Scheduled expiry check deactivated {} records", count);
            }
        } catch (Exception ex) {
            log.error("Error during scheduled expiry check", ex);
        }
    }
}
