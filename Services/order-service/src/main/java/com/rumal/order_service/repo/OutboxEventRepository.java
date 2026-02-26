package com.rumal.order_service.repo;

import com.rumal.order_service.entity.OutboxEvent;
import com.rumal.order_service.entity.OutboxEventStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findByStatusAndNextRetryAtBeforeOrStatusAndNextRetryAtIsNull(
            OutboxEventStatus status1, Instant before,
            OutboxEventStatus status2,
            Pageable pageable
    );
}
