package com.rumal.vendor_service.repo;

import com.rumal.vendor_service.entity.VendorAuditOutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface VendorAuditOutboxRepository extends JpaRepository<VendorAuditOutboxEvent, UUID> {

    List<VendorAuditOutboxEvent> findByProcessedAtIsNullAndAvailableAtLessThanEqualOrderByAvailableAtAscCreatedAtAsc(
            Instant availableAt,
            Pageable pageable
    );
}
