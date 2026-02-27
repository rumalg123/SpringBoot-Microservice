# QA Bugs & Tasks — search-service

> **Auditor**: Principal Backend QA Architect & Senior Security Bug Hunter
> **Service**: `search-service` (port 8094)
> **Date**: 2026-02-27
> **Findings**: 4 total — 1 Critical, 1 Medium, 2 Low

---

## Domain Map

| Layer | Component | Key Responsibility |
|---|---|---|
| Controller | `SearchController` (`/search`) | Public storefront: product search, autocomplete, popular searches |
| Controller | `SearchAdminController` (`/internal/search`) | Internal: trigger reindex, remove product from index |
| Service | `ProductSearchService` | Elasticsearch full-text search with function_score, facets, caching |
| Service | `AutocompleteService` | Type-ahead suggestions via ES `name.autocomplete` field + popular term matching |
| Service | `PopularSearchService` | Redis sorted set for tracking and serving popular search terms |
| Service | `ProductIndexService` | Full reindex (startup + daily cron), incremental sync (5-min cron), stale cleanup |
| Client | `ProductClient` | Fetch product catalog pages from product-service (CB + Retry: manual, not AOP) |
| Document | `ProductDocument` | Elasticsearch document with multi-field mappings, nested specs/variations |
| Repository | `ProductSearchRepository` | `ElasticsearchRepository`, `deleteByUpdatedAtBefore` |
| Config | `CacheConfig` | Redis caches: `searchResults` (5m), `autocomplete` (2m), `popularSearches` (30m) |
| Config | `CircuitBreakerConfig` | Programmatic CB + Retry registry (ignores `DownstreamHttpException`) |
| Config | `ElasticsearchConfig` | Index creation on startup |
| Config | `SchedulingConfig` | Thread pool (5) for `@Scheduled` tasks |
| Security | `InternalRequestVerifier` | HMAC-based internal auth for admin endpoints |

---

## BUG-SEARCH-001 — Stale Product Cleanup Deletes ALL Indexed Products

| Field | Value |
|---|---|
| **Severity** | **CRITICAL** |
| **Category** | Logic & Runtime |
| **Affected Files** | `service/ProductIndexService.java`, `document/ProductDocument.java` |
| **Lines** | `ProductIndexService.java:51, 84, 147–176` |

### Description

After a successful full reindex, the stale product cleanup at line 84 deletes documents where `updatedAt < reindexStart`:

```java
Instant reindexStart = Instant.now();
// ... index all products ...
long deleted = productSearchRepository.deleteByUpdatedAtBefore(reindexStart);
```

The `updatedAt` field in `ProductDocument` is set from the product's **actual** `updatedAt` timestamp from the product-service (line 175):

```java
.updatedAt(data.updatedAt())
```

Since `reindexStart = Instant.now()` and every product's `updatedAt` is a historical timestamp (when the product was last modified — days, weeks, or months ago), **every single product** satisfies `updatedAt < reindexStart`.

**Result**: After every successful full reindex, the cleanup immediately deletes ALL products from the Elasticsearch index, leaving the search completely empty. The search index is populated during the reindex, then immediately wiped by the stale cleanup.

The intent was clearly to detect products that were deleted from the product-service (they wouldn't be re-indexed, so their old `updatedAt` would remain). But since the code copies the product's actual `updatedAt` rather than recording an `indexedAt` timestamp, the logic is inverted — it deletes everything.

### Current Code

**`ProductIndexService.java:51, 82–91`** — Cleanup uses `reindexStart` against product's historical `updatedAt`:
```java
Instant reindexStart = Instant.now();
// ...
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
```

**`ProductIndexService.java:147–176`** — `toDocument` copies product's actual updatedAt:
```java
private ProductDocument toDocument(ProductIndexData data) {
    return ProductDocument.builder()
            // ...
            .updatedAt(data.updatedAt())
            .build();
}
```

### Fix

Add an `indexedAt` field to `ProductDocument` that records when the document was written to ES, and use that for stale cleanup instead of `updatedAt`.

**Step 1** — Add `indexedAt` field to `ProductDocument.java`.

Add after line 111:
```java
@Field(type = FieldType.Date, format = DateFormat.epoch_millis)
private Instant indexedAt;
```

**Step 2** — Set `indexedAt` to `Instant.now()` during indexing in `ProductIndexService.java`.

Replace `ProductIndexService.java:147–176`:
```java
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
            .indexedAt(Instant.now())
            .build();
}
```

**Step 3** — Update the repository to use `indexedAt` for cleanup.

Replace `ProductSearchRepository.java:9`:
```java
long deleteByIndexedAtBefore(Instant cutoff);
```

**Step 4** — Update the cleanup call in `ProductIndexService.java`.

Replace `ProductIndexService.java:84`:
```java
long deleted = productSearchRepository.deleteByIndexedAtBefore(reindexStart);
```

Now the stale cleanup correctly deletes only products that were NOT re-indexed during this reindex run (i.e., products that no longer exist in the product-service catalog).

---

## BUG-SEARCH-002 — Sync Operations Update lastSyncTime on Partial Failure

| Field | Value |
|---|---|
| **Severity** | **Medium** |
| **Category** | Data Integrity |
| **Affected Files** | `service/ProductIndexService.java` |
| **Lines** | `ProductIndexService.java:94, 126–128, 135` |

### Description

Both `fullReindex()` and `incrementalSync()` call `updateLastSyncTime()` **unconditionally**, even when an error caused early termination of the sync loop.

**`fullReindex()`** (line 94):
```java
// Line 75-78: on error, hadErrors=true, break
updateLastSyncTime(); // Line 94 — always called
```

**`incrementalSync()`** (line 135):
```java
// Line 126-128: on error, break
updateLastSyncTime(); // Line 135 — always called
```

**Impact for `incrementalSync()`**: If the loop processes pages 0–2 successfully but fails on page 3, the `lastSyncTime` is advanced to `Instant.now()`. The next incremental sync fetches products updated since the new timestamp, permanently skipping any products that were on page 3+ but updated before the failure timestamp. These products remain out-of-sync in the search index until the next daily full reindex (which itself may be broken per BUG-SEARCH-001).

**Impact for `fullReindex()`**: If the reindex partially fails, the `lastSyncTime` still advances. Subsequent incremental syncs use the new timestamp, potentially missing products that weren't indexed during the failed reindex.

### Current Code

**`ProductIndexService.java:49–97`** — `fullReindex()`:
```java
public ReindexResponse fullReindex() {
    // ...
    while (hasMore) {
        try { /* ... */ } catch (Exception e) {
            hadErrors = true;
            break; // Partial failure
        }
    }
    // ...
    updateLastSyncTime(); // Always called — even when hadErrors=true
    // ...
}
```

**`ProductIndexService.java:100–136`** — `incrementalSync()`:
```java
public void incrementalSync() {
    // ...
    while (hasMore) {
        try { /* ... */ } catch (Exception e) {
            break; // Partial failure
        }
    }
    // ...
    updateLastSyncTime(); // Always called — even after break on error
}
```

### Fix

**Step 1** — In `fullReindex()`, only update lastSyncTime on success.

Replace `ProductIndexService.java:94`:
```java
if (!hadErrors) {
    updateLastSyncTime();
}
```

**Step 2** — In `incrementalSync()`, track errors and only update on success.

Replace `ProductIndexService.java:100–136`:
```java
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
    boolean hadErrors = false;

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
            hadErrors = true;
            break;
        }
    }

    if (totalSynced > 0) {
        log.info("Incremental sync completed: {} products updated", totalSynced);
    }
    if (!hadErrors) {
        updateLastSyncTime();
    }
}
```

---

## BUG-SEARCH-003 — No Upper Bound on Search Page Parameter Causes Elasticsearch Errors

| Field | Value |
|---|---|
| **Severity** | **Low** |
| **Category** | Logic & Runtime |
| **Affected Files** | `controller/SearchController.java` |
| **Lines** | `SearchController.java:34–39` |

### Description

The `searchProducts` endpoint validates `size` (capped at 50, floored at 1) and `page` (floored at 0), but applies **no upper bound** on `page`:

```java
if (size > 50) size = 50;
if (size < 1) size = 1;
if (page < 0) page = 0;
// No upper bound on page
```

Elasticsearch has a default `index.max_result_window` of 10,000. The `from` parameter is computed as `page * size`. With `size=50` and `page=201`, `from = 10,050 > 10,000`, causing Elasticsearch to reject the query with an error that surfaces as a 500 to the client.

An attacker or misbehaving client can trivially trigger server errors by requesting high page numbers.

### Fix

Replace `SearchController.java:37–39`:
```java
if (size > 50) size = 50;
if (size < 1) size = 1;
if (page < 0) page = 0;
int maxPage = (10_000 / size) - 1;
if (page > maxPage) page = maxPage;
```

---

## BUG-SEARCH-004 — Concurrent Scheduled Reindex and Incremental Sync Without Mutual Exclusion

| Field | Value |
|---|---|
| **Severity** | **Low** |
| **Category** | Architecture & Resilience |
| **Affected Files** | `service/ProductIndexService.java` |
| **Lines** | `ProductIndexService.java:44–47, 100–136, 138–140` |

### Description

Three entry points can trigger product indexing:

1. `scheduledFullReindex()` — daily cron at 3 AM (line 44)
2. `incrementalSync()` — every 5 minutes (line 100)
3. `triggerFullReindex()` — manual admin trigger (line 138)

None of these acquire any lock or guard, so they can run concurrently:

- A manual admin reindex at 3:00 AM overlaps with the scheduled cron reindex.
- If `fullReindex()` takes longer than 5 minutes, an `incrementalSync()` fires while it's still running.
- Both operations page through the product-service concurrently, doubling the load on the downstream service.
- Concurrent `updateLastSyncTime()` calls create a race on the Redis key, and the later writer's timestamp wins regardless of which operation completed more data.

### Fix

Add a `ReentrantLock` to prevent concurrent execution.

**`ProductIndexService.java`** — Add a lock field after line 27:
```java
private final java.util.concurrent.locks.ReentrantLock indexLock = new java.util.concurrent.locks.ReentrantLock();
```

**`ProductIndexService.java`** — Guard `fullReindex()`. Replace line 49:
```java
public ReindexResponse fullReindex() {
    if (!indexLock.tryLock()) {
        log.warn("Reindex already in progress, skipping");
        return new ReindexResponse(0, 0, "SKIPPED");
    }
    try {
```

Add before the return at line 97:
```java
    } finally {
        indexLock.unlock();
    }
```

**`ProductIndexService.java`** — Guard `incrementalSync()`. Replace line 101:
```java
public void incrementalSync() {
    if (!indexLock.tryLock()) {
        log.debug("Index operation already in progress, skipping incremental sync");
        return;
    }
    try {
```

Add at the end of the method (before the closing `}`):
```java
    } finally {
        indexLock.unlock();
    }
```

---

## Summary

| ID | Severity | Category | Title |
|---|---|---|---|
| BUG-SEARCH-001 | **CRITICAL** | Logic & Runtime | Stale product cleanup deletes ALL indexed products — uses product's `updatedAt` instead of `indexedAt` |
| BUG-SEARCH-002 | Medium | Data Integrity | Sync operations update lastSyncTime on partial failure — products permanently missed |
| BUG-SEARCH-003 | Low | Logic & Runtime | No upper bound on search page parameter — causes Elasticsearch 500 errors |
| BUG-SEARCH-004 | Low | Architecture & Resilience | Concurrent reindex and incremental sync without mutual exclusion |
