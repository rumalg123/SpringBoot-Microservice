package com.rumal.order_service.scheduler;

import com.rumal.order_service.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderStatusAuditOutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(OrderStatusAuditOutboxProcessor.class);

    private final OrderService orderService;

    @Value("${order.audit.outbox.batch-size:50}")
    private int batchSize;

    @Scheduled(
            fixedDelayString = "${order.audit.outbox.poll-interval-ms:5000}",
            initialDelayString = "${order.audit.outbox.initial-delay-ms:10000}"
    )
    public void process() {
        try {
            orderService.processStatusAuditOutboxBatch(batchSize);
        } catch (Exception ex) {
            log.error("Order status audit outbox batch failed", ex);
        }
    }
}
