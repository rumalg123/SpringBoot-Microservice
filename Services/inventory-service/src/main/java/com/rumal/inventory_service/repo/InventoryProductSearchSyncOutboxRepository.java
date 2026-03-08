package com.rumal.inventory_service.repo;

import com.rumal.inventory_service.entity.InventoryProductSearchSyncOutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryProductSearchSyncOutboxRepository extends JpaRepository<InventoryProductSearchSyncOutboxEvent, UUID> {

    Optional<InventoryProductSearchSyncOutboxEvent> findFirstByProductIdAndProcessedAtIsNullOrderByCreatedAtAsc(UUID productId);

    List<InventoryProductSearchSyncOutboxEvent> findByProcessedAtIsNullAndAvailableAtLessThanEqualOrderByAvailableAtAscCreatedAtAsc(
            Instant now,
            Pageable pageable
    );
}
