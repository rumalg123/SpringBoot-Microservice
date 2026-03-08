package com.rumal.product_service.repo;

import com.rumal.product_service.entity.ProductInventorySyncOutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductInventorySyncOutboxRepository extends JpaRepository<ProductInventorySyncOutboxEvent, UUID> {

    Optional<ProductInventorySyncOutboxEvent> findFirstByProductIdAndProcessedAtIsNullOrderByCreatedAtAsc(UUID productId);

    List<ProductInventorySyncOutboxEvent> findByProcessedAtIsNullAndAvailableAtLessThanEqualOrderByAvailableAtAscCreatedAtAsc(
            Instant availableAt,
            Pageable pageable
    );
}
