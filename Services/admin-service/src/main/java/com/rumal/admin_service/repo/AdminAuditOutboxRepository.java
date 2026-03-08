package com.rumal.admin_service.repo;

import com.rumal.admin_service.entity.AdminAuditOutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AdminAuditOutboxRepository extends JpaRepository<AdminAuditOutboxEvent, UUID> {

    List<AdminAuditOutboxEvent> findByProcessedAtIsNullAndAvailableAtLessThanEqualOrderByAvailableAtAscCreatedAtAsc(
            Instant availableAt,
            Pageable pageable
    );
}
