package com.rumal.search_service.service;

import com.rumal.search_service.client.ProductClient;
import com.rumal.search_service.client.dto.ProductIndexData;
import com.rumal.search_service.client.dto.ProductIndexPage;
import com.rumal.search_service.document.ProductDocument;
import com.rumal.search_service.dto.ReindexResponse;
import com.rumal.search_service.repository.ProductSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductIndexService {

    private static final String LAST_SYNC_KEY = "search:last-sync";

    private final ProductClient productClient;
    private final ProductSearchRepository productSearchRepository;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${search.sync.batch-size:100}")
    private int batchSize;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (getLastSyncTime() == null) {
            log.info("No previous sync detected, triggering initial full reindex");
            fullReindex();
        }
    }

    @Scheduled(cron = "${search.sync.full-reindex-cron:0 0 3 * * *}")
    public void scheduledFullReindex() {
        fullReindex();
    }

    public ReindexResponse fullReindex() {
        log.info("Starting full product reindex");
        Instant reindexStart = Instant.now();
        long start = System.currentTimeMillis();
        long totalIndexed = 0;
        int page = 0;
        boolean hasMore = true;
        boolean hadErrors = false;

        while (hasMore) {
            try {
                ProductIndexPage batch = productClient.fetchCatalogPage(page, batchSize);
                if (batch == null || batch.content() == null || batch.content().isEmpty()) break;

                List<ProductDocument> documents = batch.content().stream()
                        .map(this::toDocument)
                        .toList();

                productSearchRepository.saveAll(documents);
                totalIndexed += documents.size();
                hasMore = !batch.last();
                page++;

                if (page % 10 == 0) {
                    log.info("Reindex progress: {} products indexed across {} pages", totalIndexed, page);
                }
            } catch (Exception e) {
                log.error("Error during full reindex at page {}: {}", page, e.getMessage(), e);
                hadErrors = true;
                break;
            }
        }

        if (!hadErrors && totalIndexed > 0) {
            try {
                long deleted = productSearchRepository.deleteByUpdatedAtBefore(reindexStart);
                if (deleted > 0) {
                    log.info("Removed {} stale products from search index", deleted);
                }
            } catch (Exception e) {
                log.warn("Failed to remove stale products: {}", e.getMessage());
            }
        }

        long duration = System.currentTimeMillis() - start;
        updateLastSyncTime();
        String status = hadErrors ? "PARTIAL_FAILURE" : "COMPLETED";
        log.info("Full reindex {}: {} products in {}ms", status, totalIndexed, duration);
        return new ReindexResponse(totalIndexed, duration, status);
    }

    @Scheduled(cron = "${search.sync.incremental-sync-cron:0 */5 * * * *}")
    public void incrementalSync() {
        Instant since = getLastSyncTime();
        if (since == null) {
            log.info("No last sync time found, skipping incremental sync (awaiting full reindex)");
            return;
        }

        log.debug("Starting incremental sync for products updated since {}", since);
        int page = 0;
        long totalSynced = 0;
        boolean hasMore = true;

        while (hasMore) {
            try {
                ProductIndexPage batch = productClient.fetchUpdatedSince(since, page, batchSize);
                if (batch == null || batch.content() == null || batch.content().isEmpty()) break;

                List<ProductDocument> documents = batch.content().stream()
                        .map(this::toDocument)
                        .toList();

                productSearchRepository.saveAll(documents);
                totalSynced += documents.size();
                hasMore = !batch.last();
                page++;
            } catch (Exception e) {
                log.warn("Error during incremental sync at page {}: {}", page, e.getMessage());
                break;
            }
        }

        if (totalSynced > 0) {
            log.info("Incremental sync completed: {} products updated", totalSynced);
        }
        updateLastSyncTime();
    }

    public ReindexResponse triggerFullReindex() {
        return fullReindex();
    }

    public void deleteProduct(String productId) {
        productSearchRepository.deleteById(productId);
        log.info("Removed product {} from search index", productId);
    }

    private ProductDocument toDocument(ProductIndexData data) {
        return ProductDocument.builder()
                .id(data.id().toString())
                .slug(data.slug())
                .name(data.name())
                .shortDescription(data.shortDescription())
                .brandName(data.brandName())
                .sku(data.sku())
                .mainImage(data.mainImage())
                .regularPrice(data.regularPrice())
                .discountedPrice(data.discountedPrice())
                .sellingPrice(data.sellingPrice())
                .mainCategory(data.mainCategory())
                .subCategories(data.subCategories() != null ? data.subCategories() : Collections.emptySet())
                .categories(data.categories() != null ? data.categories() : Collections.emptySet())
                .vendorId(data.vendorId() != null ? data.vendorId().toString() : null)
                .productType(data.productType())
                .viewCount(data.viewCount())
                .soldCount(data.soldCount())
                .active(data.active())
                .variations(data.variations() != null
                        ? data.variations().stream()
                                .map(v -> ProductDocument.VariationEntry.builder()
                                        .name(v.name()).value(v.value()).build())
                                .toList()
                        : List.of())
                .specifications(List.of())
                .createdAt(data.createdAt())
                .updatedAt(data.updatedAt())
                .build();
    }

    private Instant getLastSyncTime() {
        try {
            String value = stringRedisTemplate.opsForValue().get(LAST_SYNC_KEY);
            return value != null ? Instant.parse(value) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void updateLastSyncTime() {
        try {
            stringRedisTemplate.opsForValue().set(LAST_SYNC_KEY, Instant.now().toString());
        } catch (Exception e) {
            log.warn("Failed to update last sync time: {}", e.getMessage());
        }
    }
}
