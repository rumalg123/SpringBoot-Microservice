package com.rumal.access_service.repo;

import com.rumal.access_service.entity.AccessAuditOutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AccessAuditOutboxRepository extends JpaRepository<AccessAuditOutboxEvent, UUID> {

    List<AccessAuditOutboxEvent> findByProcessedAtIsNullAndAvailableAtLessThanEqualOrderByAvailableAtAscCreatedAtAsc(
            Instant availableAt,
            Pageable pageable
    );
}
