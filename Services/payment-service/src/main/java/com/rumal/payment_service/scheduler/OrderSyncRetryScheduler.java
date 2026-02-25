package com.rumal.payment_service.scheduler;

import com.rumal.payment_service.client.OrderClient;
import com.rumal.payment_service.entity.Payment;
import com.rumal.payment_service.entity.PaymentStatus;
import com.rumal.payment_service.repo.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

    @Scheduled(fixedDelayString = "${payment.order-sync.retry-interval:PT2M}")
    @Transactional
    public void retryPendingOrderSyncs() {
        try {
            int totalSynced = 0;
            int totalProcessed = 0;
            Page<Payment> page;

            do {
                page = paymentRepository.findOrderSyncPending(
                        List.of(SUCCESS, FAILED, CANCELLED), PageRequest.of(0, 100));

                for (Payment payment : page.getContent()) {
                    try {
                        syncOrder(payment);
                        payment.setOrderSyncPending(false);
                        paymentRepository.save(payment);
                        totalSynced++;
                    } catch (Exception ex) {
                        log.warn("Order sync retry failed for payment {}. Will retry next cycle.",
                                payment.getId(), ex);
                    }
                }
                totalProcessed += page.getNumberOfElements();
            } while (!page.isEmpty());

            if (totalProcessed > 0) {
                log.info("Order sync retry: {}/{} synced successfully", totalSynced, totalProcessed);
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
