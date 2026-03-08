package com.rumal.product_service.service;

import com.rumal.product_service.entity.ProductInventorySyncOutboxEvent;
import com.rumal.product_service.repo.ProductInventorySyncOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductInventorySyncOutboxService {

    private final ProductInventorySyncOutboxRepository productInventorySyncOutboxRepository;

    @Transactional(readOnly = false)
    public void enqueue(UUID productId) {
        if (productId == null) {
            return;
        }

        Instant now = Instant.now();
        productInventorySyncOutboxRepository.findFirstByProductIdAndProcessedAtIsNullOrderByCreatedAtAsc(productId)
                .ifPresentOrElse(existing -> {
                    existing.setAvailableAt(now);
                    existing.setLastError(null);
                    productInventorySyncOutboxRepository.save(existing);
                }, () -> productInventorySyncOutboxRepository.save(ProductInventorySyncOutboxEvent.builder()
                        .productId(productId)
                        .availableAt(now)
                        .build()));
    }
}
