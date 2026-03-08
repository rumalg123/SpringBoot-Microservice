package com.rumal.inventory_service.service;

import com.rumal.inventory_service.entity.InventoryProductSearchSyncOutboxEvent;
import com.rumal.inventory_service.repo.InventoryProductSearchSyncOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryProductSearchSyncOutboxService {

    private final InventoryProductSearchSyncOutboxRepository repository;

    @Transactional(readOnly = false)
    public void enqueue(UUID productId) {
        if (productId == null) {
            return;
        }

        Instant now = Instant.now();
        repository.findFirstByProductIdAndProcessedAtIsNullOrderByCreatedAtAsc(productId)
                .ifPresentOrElse(existing -> {
                    existing.setAvailableAt(now);
                    existing.setLastError(null);
                    repository.save(existing);
                }, () -> repository.save(InventoryProductSearchSyncOutboxEvent.builder()
                        .productId(productId)
                        .availableAt(now)
                        .build()));
    }

    @Transactional(readOnly = false)
    public void enqueueAll(Collection<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return;
        }

        Set<UUID> distinctIds = new LinkedHashSet<>();
        for (UUID productId : productIds) {
            if (productId != null) {
                distinctIds.add(productId);
            }
        }
        for (UUID productId : distinctIds) {
            enqueue(productId);
        }
    }
}
