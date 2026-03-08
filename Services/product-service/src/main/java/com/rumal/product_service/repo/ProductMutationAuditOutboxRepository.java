package com.rumal.product_service.repo;

import com.rumal.product_service.entity.ProductMutationAuditOutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ProductMutationAuditOutboxRepository extends JpaRepository<ProductMutationAuditOutboxEvent, UUID> {

    List<ProductMutationAuditOutboxEvent> findByProcessedAtIsNullAndAvailableAtLessThanEqualOrderByAvailableAtAscCreatedAtAsc(
            Instant availableAt,
            Pageable pageable
    );
}
