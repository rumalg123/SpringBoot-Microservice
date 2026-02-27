package com.rumal.payment_service.scheduler;

import com.rumal.payment_service.client.OrderClient;
import com.rumal.payment_service.entity.Payment;
import com.rumal.payment_service.entity.PaymentStatus;
import com.rumal.payment_service.repo.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static com.rumal.payment_service.entity.PaymentStatus.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderSyncRetryScheduler {

    private final PaymentRepository paymentRepository;
    private final OrderClient orderClient;
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
                                List.of(SUCCESS, FAILED, CANCELLED), PageRequest.of(0, 100)));

                if (page == null || page.isEmpty()) break;

                for (Payment payment : page.getContent()) {
                    try {
                        syncOrder(payment);
                        transactionTemplate.executeWithoutResult(status -> {
                            Payment p = paymentRepository.findById(payment.getId()).orElse(null);
                            if (p != null) {
                                p.setOrderSyncPending(false);
                                paymentRepository.save(p);
                            }
                        });
                        totalSynced++;
                    } catch (Exception ex) {
                        transactionTemplate.executeWithoutResult(status -> {
                            Payment p = paymentRepository.findById(payment.getId()).orElse(null);
                            if (p != null) {
                                p.setOrderSyncRetryCount(p.getOrderSyncRetryCount() + 1);
                                if (p.getOrderSyncRetryCount() >= p.getOrderSyncMaxRetries()) {
                                    p.setOrderSyncPending(false);
                                    log.error("Order sync permanently failed for payment {} after {} retries",
                                            p.getId(), p.getOrderSyncRetryCount());
                                }
                                paymentRepository.save(p);
                            }
                        });
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

    private void syncOrder(Payment payment) {
        if (payment.getStatus() == SUCCESS) {
            orderClient.setPaymentInfo(
                    payment.getOrderId(),
                    payment.getId().toString(),
                    payment.getPaymentMethod(),
                    payment.getPayherePaymentId());
            orderClient.updateOrderStatus(
                    payment.getOrderId(), "CONFIRMED", "Payment confirmed via PayHere (sync retry)");
        } else {
            orderClient.updateOrderStatus(
                    payment.getOrderId(), "PAYMENT_FAILED",
                    "Payment " + payment.getStatus().name().toLowerCase() + " (sync retry)");
        }
    }
}
