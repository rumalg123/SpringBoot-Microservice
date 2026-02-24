package com.rumal.payment_service.scheduler;

import com.rumal.payment_service.service.RefundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefundEscalationScheduler {

    private final RefundService refundService;

    /**
     * Check for refund requests where vendor hasn't responded within the deadline.
     * Runs based on configured interval (default every 1 hour).
     */
    @Scheduled(fixedDelayString = "${payment.refund.escalation-check-interval:PT1H}")
    public void escalateExpiredRefunds() {
        log.debug("Running refund escalation check...");
        try {
            refundService.escalateExpiredRefunds();
        } catch (Exception e) {
            log.error("Error during refund escalation check", e);
        }
    }
}
