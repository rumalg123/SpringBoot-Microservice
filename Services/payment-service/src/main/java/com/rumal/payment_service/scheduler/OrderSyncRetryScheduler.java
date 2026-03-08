package com.rumal.payment_service.scheduler;

import com.rumal.payment_service.entity.Payment;
import com.rumal.payment_service.repo.PaymentRepository;
import com.rumal.payment_service.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static com.rumal.payment_service.entity.PaymentStatus.CANCELLED;
import static com.rumal.payment_service.entity.PaymentStatus.EXPIRED;
import static com.rumal.payment_service.entity.PaymentStatus.FAILED;
import static com.rumal.payment_service.entity.PaymentStatus.SUCCESS;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderSyncRetryScheduler {

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(fixedDelayString = "${payment.order-sync.retry-interval:PT2M}")
    public void retryPendingOrderSyncs() {
        try {
            int totalSynced = 0;
            int totalFailed = 0;
            Page<Payment> page;

            do {
                page = transactionTemplate.execute(status ->
                        paymentRepository.findOrderSyncPending(
                                List.of(SUCCESS, FAILED, CANCELLED, EXPIRED), PageRequest.of(0, 100)));

                if (page == null || page.isEmpty()) {
                    break;
                }

                for (Payment payment : page.getContent()) {
                    try {
                        paymentService.reconcilePendingOrderSync(payment.getId());
                        totalSynced++;
                    } catch (Exception ex) {
                        totalFailed++;
                        log.warn("Order sync retry failed for payment {}. Attempt {}/{}.",
                                payment.getId(), payment.getOrderSyncRetryCount() + 1,
                                payment.getOrderSyncMaxRetries(), ex);
                    }
                }
            } while (!page.isEmpty());

            if (totalSynced > 0 || totalFailed > 0) {
                log.info("Order sync retry: {} synced, {} failed", totalSynced, totalFailed);
            }
        } catch (Exception ex) {
            log.error("Error during order sync retry", ex);
        }
    }
}
