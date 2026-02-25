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
            List<Payment> pending = paymentRepository.findOrderSyncPending(
                    List.of(SUCCESS, FAILED, CANCELLED));

            for (Payment payment : pending) {
                try {
                    syncOrder(payment);
                    payment.setOrderSyncPending(false);
                    paymentRepository.save(payment);
                } catch (Exception ex) {
                    log.warn("Order sync retry failed for payment {}. Will retry next cycle.",
                            payment.getId(), ex);
                }
            }

            if (!pending.isEmpty()) {
                long synced = pending.stream().filter(p -> !p.isOrderSyncPending()).count();
                log.info("Order sync retry: {}/{} synced successfully", synced, pending.size());
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
