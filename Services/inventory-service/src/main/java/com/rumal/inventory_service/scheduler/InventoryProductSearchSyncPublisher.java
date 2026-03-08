package com.rumal.inventory_service.scheduler;

import com.rumal.inventory_service.client.ProductSearchSyncClient;
import com.rumal.inventory_service.entity.InventoryProductSearchSyncOutboxEvent;
import com.rumal.inventory_service.repo.InventoryProductSearchSyncOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryProductSearchSyncPublisher {

    private final InventoryProductSearchSyncOutboxRepository repository;
    private final ProductSearchSyncClient productSearchSyncClient;

    @Value("${inventory.search-sync.enabled:true}")
    private boolean enabled;

    @Value("${inventory.search-sync.batch-size:50}")
    private int batchSize;

    @Value("${inventory.search-sync.retry-base-delay-seconds:15}")
    private long retryBaseDelaySeconds;

    @Value("${inventory.search-sync.retry-max-delay-seconds:900}")
    private long retryMaxDelaySeconds;

    @Scheduled(
            fixedDelayString = "${inventory.search-sync.poll-interval-ms:5000}",
            initialDelayString = "${inventory.search-sync.initial-delay-ms:10000}"
    )
    public void publishPending() {
        if (!enabled) {
            return;
        }

        List<InventoryProductSearchSyncOutboxEvent> dueEvents = repository
                .findByProcessedAtIsNullAndAvailableAtLessThanEqualOrderByAvailableAtAscCreatedAtAsc(
                        Instant.now(),
                        PageRequest.of(0, Math.max(1, batchSize))
                );

        for (InventoryProductSearchSyncOutboxEvent event : dueEvents) {
            process(event);
        }
    }

    protected void process(InventoryProductSearchSyncOutboxEvent event) {
        try {
            productSearchSyncClient.requestSearchSync(event.getProductId());
            markSucceeded(event);
        } catch (Exception ex) {
            scheduleRetry(event, ex);
        }
    }

    private void markSucceeded(InventoryProductSearchSyncOutboxEvent event) {
        event.setProcessedAt(Instant.now());
        event.setAvailableAt(Instant.now());
        event.setLastError(null);
        repository.save(event);
    }

    private void scheduleRetry(InventoryProductSearchSyncOutboxEvent event, Exception ex) {
        int nextAttempt = event.getAttemptCount() + 1;
        event.setAttemptCount(nextAttempt);
        event.setLastError(truncate(ex.getMessage()));
        event.setAvailableAt(Instant.now().plusSeconds(resolveDelaySeconds(nextAttempt)));
        repository.save(event);
        log.warn("Inventory search sync retry scheduled for productId={} attempt={} error={}",
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
