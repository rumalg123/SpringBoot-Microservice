package com.rumal.order_service.service;

import com.rumal.order_service.entity.Order;
import com.rumal.order_service.entity.OrderStatus;
import com.rumal.order_service.entity.OrderStatusAuditOutboxEvent;
import com.rumal.order_service.entity.VendorOrder;
import com.rumal.order_service.repo.OrderStatusAuditOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class OrderStatusAuditRecorder {

    private final OrderStatusAuditOutboxRepository orderStatusAuditOutboxRepository;
    private final OrderAuditRequestContextResolver orderAuditRequestContextResolver;
    private final OrderAuditPayloadSanitizer orderAuditPayloadSanitizer;

    public void recordStatusAudit(
            Order order,
            OrderStatus fromStatus,
            OrderStatus toStatus,
            String actorSub,
            String actorRoles,
            String actorType,
            String changeSource,
            String note
    ) {
        if (order == null || order.getId() == null || toStatus == null) {
            return;
        }
        OrderAuditRequestContext context = orderAuditRequestContextResolver.resolve(actorSub, actorRoles, actorType, changeSource);
        orderStatusAuditOutboxRepository.save(OrderStatusAuditOutboxEvent.builder()
                .auditScope("ORDER")
                .orderId(order.getId())
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .actorSub(defaultValue(context.actorSub(), "system"))
                .actorTenantId(trimToNull(context.actorTenantId()))
                .actorRoles(trimToNull(context.actorRoles()))
                .actorType(defaultValue(context.actorType(), "SYSTEM"))
                .changeSource(defaultValue(context.changeSource(), "SYSTEM"))
                .note(orderAuditPayloadSanitizer.sanitizeNote(note))
                .changeSet(orderAuditPayloadSanitizer.buildStatusChangeSet(fromStatus, toStatus, note))
                .clientIp(trimToNull(context.clientIp()))
                .userAgent(trimToNull(context.userAgent()))
                .requestId(trimToNull(context.requestId()))
                .availableAt(Instant.now())
                .build());
    }

    public void recordVendorOrderStatusAudit(
            VendorOrder vendorOrder,
            OrderStatus fromStatus,
            OrderStatus toStatus,
            String actorSub,
            String actorRoles,
            String actorType,
            String changeSource,
            String note
    ) {
        if (vendorOrder == null || vendorOrder.getId() == null || toStatus == null) {
            return;
        }
        OrderAuditRequestContext context = orderAuditRequestContextResolver.resolve(actorSub, actorRoles, actorType, changeSource);
        orderStatusAuditOutboxRepository.save(OrderStatusAuditOutboxEvent.builder()
                .auditScope("VENDOR_ORDER")
                .orderId(vendorOrder.getOrder() == null ? null : vendorOrder.getOrder().getId())
                .vendorOrderId(vendorOrder.getId())
                .vendorId(vendorOrder.getVendorId())
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .actorSub(defaultValue(context.actorSub(), "system"))
                .actorTenantId(trimToNull(context.actorTenantId()))
                .actorRoles(trimToNull(context.actorRoles()))
                .actorType(defaultValue(context.actorType(), "SYSTEM"))
                .changeSource(defaultValue(context.changeSource(), "SYSTEM"))
                .note(orderAuditPayloadSanitizer.sanitizeNote(note))
                .changeSet(orderAuditPayloadSanitizer.buildStatusChangeSet(fromStatus, toStatus, note))
                .clientIp(trimToNull(context.clientIp()))
                .userAgent(trimToNull(context.userAgent()))
                .requestId(trimToNull(context.requestId()))
                .availableAt(Instant.now())
                .build());
    }

    private String defaultValue(String value, String fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
