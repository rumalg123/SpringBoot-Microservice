package com.rumal.order_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rumal.order_service.entity.Order;
import com.rumal.order_service.entity.OrderStatus;
import com.rumal.order_service.entity.OrderStatusAudit;
import com.rumal.order_service.entity.OutboxEvent;
import com.rumal.order_service.entity.OutboxEventStatus;
import com.rumal.order_service.entity.VendorOrder;
import com.rumal.order_service.entity.VendorOrderStatusAudit;
import com.rumal.order_service.exception.ResourceNotFoundException;
import com.rumal.order_service.repo.OrderRepository;
import com.rumal.order_service.repo.OrderStatusAuditRepository;
import com.rumal.order_service.repo.OutboxEventRepository;
import com.rumal.order_service.repo.VendorOrderRepository;
import com.rumal.order_service.repo.VendorOrderStatusAuditRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderSagaCompensationService {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaCompensationService.class);

    private final OrderRepository orderRepository;
    private final VendorOrderRepository vendorOrderRepository;
    private final OrderStatusAuditRepository orderStatusAuditRepository;
    private final VendorOrderStatusAuditRepository vendorOrderStatusAuditRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 20)
    public void compensatePermanentFailure(OutboxEvent failedEvent, String failureMessage) {
        if (failedEvent == null || failedEvent.getAggregateId() == null) {
            return;
        }

        Order order = orderRepository.findByIdForUpdate(failedEvent.getAggregateId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order not found for outbox compensation: " + failedEvent.getAggregateId()));

        if (!isCompensatable(order.getStatus())) {
            log.error("Skipping automatic compensation for order {} in status {} after {} failure. Manual review required.",
                    order.getId(), order.getStatus(), failedEvent.getEventType());
            return;
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.info("Order {} already cancelled while compensating permanent outbox failure {}", order.getId(), failedEvent.getEventType());
            return;
        }

        String compensationReason = buildCompensationReason(failedEvent.getEventType(), failureMessage);
        OrderStatus previousStatus = order.getStatus();
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        orderStatusAuditRepository.save(OrderStatusAudit.builder()
                .order(order)
                .fromStatus(previousStatus)
                .toStatus(OrderStatus.CANCELLED)
                .actorType("system")
                .changeSource("outbox_compensation")
                .note(truncateAuditNote(compensationReason))
                .build());

        if (order.getVendorOrders() != null) {
            for (VendorOrder vendorOrder : order.getVendorOrders()) {
                if (vendorOrder.getStatus() == OrderStatus.CANCELLED) {
                    continue;
                }
                OrderStatus previousVendorStatus = vendorOrder.getStatus();
                vendorOrder.setStatus(OrderStatus.CANCELLED);
                vendorOrderRepository.save(vendorOrder);
                vendorOrderStatusAuditRepository.save(VendorOrderStatusAudit.builder()
                        .vendorOrder(vendorOrder)
                        .fromStatus(previousVendorStatus)
                        .toStatus(OrderStatus.CANCELLED)
                        .actorType("system")
                        .changeSource("outbox_compensation")
                        .note(truncateAuditNote(compensationReason))
                        .build());
            }
        }

        enqueueOutboxEvent(order.getId(), "RELEASE_INVENTORY_RESERVATION", Map.of(
                "reason", compensationReason
        ));

        if (order.getCouponReservationId() != null) {
            enqueueOutboxEvent(order.getId(), "RELEASE_COUPON_RESERVATION", Map.of(
                    "reservationId", order.getCouponReservationId().toString(),
                    "reason", compensationReason
            ));
        }

        log.error("Cancelled order {} after permanent outbox failure {}. Reason: {}",
                order.getId(), failedEvent.getEventType(), compensationReason);
    }

    private void enqueueOutboxEvent(UUID aggregateId, String eventType, Map<String, Object> payload) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize compensation outbox payload for " + eventType, ex);
        }

        OutboxEvent event = OutboxEvent.builder()
                .aggregateType("Order")
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(payloadJson)
                .status(OutboxEventStatus.PENDING)
                .build();
        outboxEventRepository.save(event);
    }

    private boolean isCompensatable(OrderStatus status) {
        return status == OrderStatus.PENDING
                || status == OrderStatus.PAYMENT_PENDING
                || status == OrderStatus.PAYMENT_FAILED
                || status == OrderStatus.CANCELLED;
    }

    private String buildCompensationReason(String eventType, String failureMessage) {
        String normalizedEventType = eventType == null
                ? "unknown_event"
                : eventType.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        String base = "saga_compensation_" + normalizedEventType;
        if (failureMessage == null || failureMessage.isBlank()) {
            return truncateReason(base);
        }
        return truncateReason(base + ": " + failureMessage.trim());
    }

    private String truncateReason(String value) {
        if (value == null) {
            return "saga_compensation";
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    private String truncateAuditNote(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 240 ? value.substring(0, 240) : value;
    }
}
