package com.rumal.order_service.repo;

import com.rumal.order_service.entity.OrderStatusAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderStatusAuditRepository extends JpaRepository<OrderStatusAudit, UUID> {
    List<OrderStatusAudit> findByOrderIdOrderByCreatedAtDesc(UUID orderId);
}
