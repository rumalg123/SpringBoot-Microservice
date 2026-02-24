package com.rumal.payment_service.scheduler;

import com.rumal.payment_service.entity.Payment;
import com.rumal.payment_service.entity.PaymentAudit;
import com.rumal.payment_service.entity.PaymentStatus;
import com.rumal.payment_service.repo.PaymentAuditRepository;
import com.rumal.payment_service.repo.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentExpiryScheduler {

    private final PaymentRepository paymentRepository;
    private final PaymentAuditRepository auditRepository;

    /**
     * Expire INITIATED payments that have passed their expiresAt time.
     * Runs based on configured interval (default every 5 minutes).
     */
    @Scheduled(fixedDelayString = "${payment.expiry.check-interval:PT5M}")
    @Transactional
    public void expireStalePayments() {
        log.debug("Running payment expiry check...");
        try {
            List<Payment> stalePayments = paymentRepository.findByStatusAndExpiresAtBefore(
                    PaymentStatus.INITIATED, Instant.now()
            );

            if (stalePayments.isEmpty()) {
                return;
            }

            for (Payment payment : stalePayments) {
                String oldStatus = payment.getStatus().name();
                payment.setStatus(PaymentStatus.EXPIRED);
                paymentRepository.save(payment);

                auditRepository.save(PaymentAudit.builder()
                        .paymentId(payment.getId())
                        .eventType("PAYMENT_EXPIRED")
                        .fromStatus(oldStatus)
                        .toStatus("EXPIRED")
                        .actorType("system")
                        .note("Payment expired after timeout")
                        .build());
            }

            log.info("Expired {} stale payments", stalePayments.size());
        } catch (Exception e) {
            log.error("Error during payment expiry check", e);
        }
    }
}
