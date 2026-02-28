package com.rumal.payment_service.scheduler;

import com.rumal.payment_service.entity.Payment;
import com.rumal.payment_service.entity.PaymentAudit;
import com.rumal.payment_service.entity.PaymentStatus;
import com.rumal.payment_service.repo.PaymentAuditRepository;
import com.rumal.payment_service.repo.PaymentRepository;
import com.rumal.payment_service.service.PaymentAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentExpiryScheduler {

    private final PaymentRepository paymentRepository;
    private final PaymentAuditRepository auditRepository;
    private final PaymentAuditService paymentAuditService;
    private final TransactionTemplate transactionTemplate;

    /**
     * Expire INITIATED payments that have passed their expiresAt time.
     * Runs based on configured interval (default every 5 minutes).
     * Each payment is expired in its own transaction to prevent one failure from rolling back the entire batch.
     */
    @Scheduled(fixedDelayString = "${payment.expiry.check-interval:PT5M}")
    public void expireStalePayments() {
        log.debug("Running payment expiry check...");
        int totalExpired = 0;
        int totalFailed = 0;
        Page<Payment> page;

        do {
            page = paymentRepository.findByStatusAndExpiresAtBefore(
                    PaymentStatus.INITIATED, Instant.now(), PageRequest.of(0, 100)
            );

            for (Payment payment : page.getContent()) {
                UUID paymentId = payment.getId();
                try {
                    transactionTemplate.executeWithoutResult(status -> {
                        Payment p = paymentRepository.findById(paymentId).orElse(null);
                        if (p == null || p.getStatus() != PaymentStatus.INITIATED) {
                            return;
                        }
                        String oldStatus = p.getStatus().name();
                        p.setStatus(PaymentStatus.EXPIRED);
                        paymentRepository.save(p);

                        auditRepository.save(PaymentAudit.builder()
                                .paymentId(p.getId())
                                .eventType("PAYMENT_EXPIRED")
                                .fromStatus(oldStatus)
                                .toStatus("EXPIRED")
                                .actorType("system")
                                .note("Payment expired after timeout")
                                .build());
                    });
                    totalExpired++;
                } catch (Exception e) {
                    totalFailed++;
                    log.error("Failed to expire payment {}: {}", paymentId, e.getMessage());
                }
            }
        } while (!page.isEmpty());

        if (totalExpired > 0) {
            log.info("Expired {} stale payments", totalExpired);
        }
        if (totalFailed > 0) {
            log.warn("Failed to expire {} payments", totalFailed);
        }
    }
}
