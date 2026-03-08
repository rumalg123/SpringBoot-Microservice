package com.rumal.payment_service.scheduler;

import com.rumal.payment_service.entity.Payment;
import com.rumal.payment_service.entity.PaymentAudit;
import com.rumal.payment_service.entity.PaymentStatus;
import com.rumal.payment_service.repo.PaymentAuditRepository;
import com.rumal.payment_service.repo.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentExpiryScheduler {

    private final PaymentRepository paymentRepository;
    private final PaymentAuditRepository auditRepository;
    private final TransactionTemplate transactionTemplate;

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
                        Payment current = paymentRepository.findById(paymentId).orElse(null);
                        if (current == null || current.getStatus() != PaymentStatus.INITIATED) {
                            return;
                        }

                        String oldStatus = current.getStatus().name();
                        current.setStatus(PaymentStatus.EXPIRED);
                        current.setOrderSyncPending(true);
                        current.setOrderSyncRetryCount(0);
                        current.setOrderSyncFailed(false);
                        paymentRepository.save(current);

                        auditRepository.save(PaymentAudit.builder()
                                .paymentId(current.getId())
                                .eventType("PAYMENT_EXPIRED")
                                .fromStatus(oldStatus)
                                .toStatus("EXPIRED")
                                .actorType("system")
                                .note("Payment expired after timeout")
                                .build());
                    });
                    totalExpired++;
                } catch (Exception ex) {
                    totalFailed++;
                    log.error("Failed to expire payment {}: {}", paymentId, ex.getMessage());
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
