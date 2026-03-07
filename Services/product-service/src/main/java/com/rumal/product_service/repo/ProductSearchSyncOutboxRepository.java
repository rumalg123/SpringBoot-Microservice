package com.rumal.product_service.repo;

import com.rumal.product_service.entity.ProductSearchSyncOutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductSearchSyncOutboxRepository extends JpaRepository<ProductSearchSyncOutboxEvent, UUID> {

    Optional<ProductSearchSyncOutboxEvent> findFirstByProductIdAndProcessedAtIsNullOrderByCreatedAtAsc(UUID productId);

    List<ProductSearchSyncOutboxEvent> findByProcessedAtIsNullAndAvailableAtLessThanEqualOrderByAvailableAtAscCreatedAtAsc(
            Instant availableAt,
            Pageable pageable
    );
}
