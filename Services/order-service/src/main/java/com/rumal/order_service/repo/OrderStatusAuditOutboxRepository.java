package com.rumal.order_service.repo;

import com.rumal.order_service.entity.OrderStatusAuditOutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OrderStatusAuditOutboxRepository extends JpaRepository<OrderStatusAuditOutboxEvent, UUID> {

    List<OrderStatusAuditOutboxEvent> findByProcessedAtIsNullAndAvailableAtLessThanEqualOrderByAvailableAtAscCreatedAtAsc(
            Instant availableAt,
            Pageable pageable
    );
}
