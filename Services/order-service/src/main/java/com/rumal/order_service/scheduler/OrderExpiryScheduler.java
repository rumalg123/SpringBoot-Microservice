package com.rumal.order_service.scheduler;

import com.rumal.order_service.entity.Order;
import com.rumal.order_service.entity.OrderStatus;
import com.rumal.order_service.entity.OrderStatusAudit;
import com.rumal.order_service.entity.VendorOrder;
import com.rumal.order_service.entity.VendorOrderStatusAudit;
import com.rumal.order_service.repo.OrderRepository;
import com.rumal.order_service.repo.OrderStatusAuditRepository;
import com.rumal.order_service.repo.VendorOrderRepository;
import com.rumal.order_service.repo.VendorOrderStatusAuditRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class OrderExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderExpiryScheduler.class);

    private static final Set<OrderStatus> EXPIRABLE_STATUSES = EnumSet.of(
            OrderStatus.PENDING,
            OrderStatus.PAYMENT_PENDING
    );

    private final OrderRepository orderRepository;
    private final VendorOrderRepository vendorOrderRepository;
    private final OrderStatusAuditRepository orderStatusAuditRepository;
    private final VendorOrderStatusAuditRepository vendorOrderStatusAuditRepository;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(fixedDelayString = "${order.expiry.check-interval:PT5M}")
    public void cancelExpiredOrders() {
        List<Order> expired = orderRepository.findExpiredOrders(EXPIRABLE_STATUSES, Instant.now(), PageRequest.of(0, 500));
        if (expired.isEmpty()) {
            return;
        }
        log.info("Found {} expired orders to cancel", expired.size());
        for (Order order : expired) {
            try {
                transactionTemplate.executeWithoutResult(status -> cancelExpiredOrder(order));
            } catch (Exception ex) {
                log.error("Failed to cancel expired order {}: {}", order.getId(), ex.getMessage(), ex);
            }
        }
    }

    private void cancelExpiredOrder(Order order) {
        OrderStatus previousStatus = order.getStatus();
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        orderStatusAuditRepository.save(OrderStatusAudit.builder()
                .order(order)
                .fromStatus(previousStatus)
                .toStatus(OrderStatus.CANCELLED)
                .actorType("system")
                .changeSource("order_expired")
                .note("Order expired and auto-cancelled")
                .build());

        if (order.getVendorOrders() != null) {
            for (VendorOrder vo : order.getVendorOrders()) {
                if (vo.getStatus() != OrderStatus.CANCELLED) {
                    OrderStatus voPrevious = vo.getStatus();
                    vo.setStatus(OrderStatus.CANCELLED);
                    vendorOrderRepository.save(vo);
                    vendorOrderStatusAuditRepository.save(VendorOrderStatusAudit.builder()
                            .vendorOrder(vo)
                            .fromStatus(voPrevious)
                            .toStatus(OrderStatus.CANCELLED)
                            .actorType("system")
                            .changeSource("order_expired")
                            .note("Vendor order cancelled due to parent order expiry")
                            .build());
                }
            }
        }

        log.info("Expired order {} cancelled (was {})", order.getId(), previousStatus);
    }
}
