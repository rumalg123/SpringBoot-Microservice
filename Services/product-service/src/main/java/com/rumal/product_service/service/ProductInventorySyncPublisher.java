package com.rumal.product_service.service;

import com.rumal.product_service.client.InventoryCatalogClient;
import com.rumal.product_service.dto.InventoryCatalogSyncRequest;
import com.rumal.product_service.entity.ProductInventorySyncOutboxEvent;
import com.rumal.product_service.repo.ProductInventorySyncOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductInventorySyncPublisher {

    private final ProductInventorySyncOutboxRepository productInventorySyncOutboxRepository;
    private final ProductInventorySyncPayloadFactory productInventorySyncPayloadFactory;
    private final InventoryCatalogClient inventoryCatalogClient;

    @Value("${product.inventory-sync.enabled:true}")
    private boolean enabled;

    @Value("${product.inventory-sync.batch-size:50}")
    private int batchSize;

    @Value("${product.inventory-sync.retry-base-delay-seconds:15}")
    private long retryBaseDelaySeconds;

    @Value("${product.inventory-sync.retry-max-delay-seconds:900}")
    private long retryMaxDelaySeconds;

    @Scheduled(
            fixedDelayString = "${product.inventory-sync.poll-interval-ms:5000}",
            initialDelayString = "${product.inventory-sync.initial-delay-ms:10000}"
    )
    public void publishPending() {
        if (!enabled) {
            return;
        }

        List<ProductInventorySyncOutboxEvent> dueEvents = productInventorySyncOutboxRepository
                .findByProcessedAtIsNullAndAvailableAtLessThanEqualOrderByAvailableAtAscCreatedAtAsc(
                        Instant.now(),
                        PageRequest.of(0, Math.max(1, batchSize))
                );

        for (ProductInventorySyncOutboxEvent event : dueEvents) {
            process(event);
        }
    }

    protected void process(ProductInventorySyncOutboxEvent event) {
        try {
            Optional<InventoryCatalogSyncRequest> payload = productInventorySyncPayloadFactory.build(event.getProductId());
            if (payload.isPresent()) {
                inventoryCatalogClient.upsertProduct(payload.get());
            } else {
                inventoryCatalogClient.deleteProduct(event.getProductId());
            }
            markSucceeded(event);
        } catch (Exception ex) {
            scheduleRetry(event, ex);
        }
    }

    private void markSucceeded(ProductInventorySyncOutboxEvent event) {
        event.setProcessedAt(Instant.now());
        event.setAvailableAt(Instant.now());
        event.setLastError(null);
        productInventorySyncOutboxRepository.save(event);
    }

    private void scheduleRetry(ProductInventorySyncOutboxEvent event, Exception ex) {
        int nextAttempt = event.getAttemptCount() + 1;
        event.setAttemptCount(nextAttempt);
        event.setLastError(truncate(ex.getMessage()));
        event.setAvailableAt(Instant.now().plusSeconds(resolveDelaySeconds(nextAttempt)));
        productInventorySyncOutboxRepository.save(event);
        log.warn("Product inventory sync retry scheduled for productId={} attempt={} error={}",
                event.getProductId(), nextAttempt, truncate(ex.getMessage()));
    }

    private long resolveDelaySeconds(int attemptCount) {
        long safeBaseDelay = Math.max(1, retryBaseDelaySeconds);
        long safeMaxDelay = Math.max(safeBaseDelay, retryMaxDelaySeconds);
        int exponent = Math.max(0, Math.min(attemptCount - 1, 10));
        long multiplier = 1L << exponent;
        long candidate = safeBaseDelay * multiplier;
        return Math.min(candidate, safeMaxDelay);
    }

    private String truncate(String message) {
        if (message == null) {
            return "unknown-error";
        }
        return message.length() > 900 ? message.substring(0, 900) : message;
    }
}
