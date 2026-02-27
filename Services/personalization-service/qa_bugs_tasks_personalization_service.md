# QA Bugs & Tasks — personalization-service

> **Auditor**: Principal Backend QA Architect & Senior Security Bug Hunter
> **Service**: `personalization-service` (port 8089)
> **Date**: 2026-02-27
> **Findings**: 4 total — 0 Critical, 1 High, 2 Medium, 1 Low

---

## Domain Map

| Layer | Component | Key Responsibility |
|---|---|---|
| Controller | `EventController` (`/personalization/events`) | Record user events (POST, batch) |
| Controller | `PersonalizationController` (`/personalization`) | Recommendations, recently-viewed, trending |
| Controller | `ProductRecommendationController` (`/personalization/products`) | Similar products, bought-together |
| Controller | `SessionController` (`/personalization/sessions`) | Anonymous-to-user session merge |
| Service | `EventService` | Event recording, anonymous session upsert |
| Service | `SessionService` | Session merge (events + recently-viewed + cache eviction) |
| Service | `RecommendationService` | Personalized recommendations (user + anonymous) |
| Service | `RecentlyViewedService` | Redis-backed recently viewed products (sorted sets) |
| Service | `TrendingService` | Weighted trending products query + product hydration |
| Service | `SimilarProductsService` | Product similarity lookup + product hydration |
| Service | `BoughtTogetherService` | Co-purchase lookup + product hydration |
| Service | `ComputationJobService` | 5 scheduled jobs: co-purchase, similarity, affinity, trending cache evict, cleanup |
| Repository | `UserEventRepository` | Event CRUD, trending aggregation, purchase history, cleanup |
| Repository | `UserAffinityRepository` | User category/brand affinity queries |
| Repository | `CoPurchaseRepository` | Co-purchase pair queries + stale cleanup |
| Repository | `ProductSimilarityRepository` | Similarity score queries + stale cleanup |
| Repository | `AnonymousSessionRepository` | Anonymous session CRUD + stale cleanup |
| Entity | `UserEvent` | userId, sessionId, eventType, productId, categorySlugs, vendorId, brandName, price |
| Entity | `UserAffinity` | Composite PK (userId, affinityType, affinityKey), score, eventCount |
| Entity | `CoPurchase` | Composite PK (productIdA, productIdB), coPurchaseCount |
| Entity | `ProductSimilarity` | Composite PK (productId, similarProductId), score |
| Entity | `AnonymousSession` | sessionId PK, userId, createdAt, lastActivityAt, mergedAt |
| Client | `ProductClient` | Batch product summaries (CB + Retry: person-product-client) |
| Config | `CacheConfig` | Redis caching with per-cache TTLs, error handler, startup cleaner |
| Config | `CircuitBreakerConfig` | Programmatic CB + Retry via RetryRegistry |
| Config | `SchedulingConfig` | Thread pool scheduler (5 threads) |

---

## BUG-PERS-001 — Scheduled Computation Jobs Load Unbounded Data Into Memory

| Field | Value |
|---|---|
| **Severity** | **HIGH** |
| **Category** | Architecture & Resilience |
| **Affected Files** | `service/ComputationJobService.java`, `repository/UserEventRepository.java` |
| **Lines** | `ComputationJobService.java:44–117`, `ComputationJobService.java:241–344`, `UserEventRepository.java:60–65`, `UserEventRepository.java:76–85` |

### Description

Two scheduled computation methods load the entire result set into memory with no pagination or row limit:

1. **`computeCoPurchases()`** (line 48) calls `findPurchaseEventsForCoPurchase(since)` which returns **all** purchase events for the last 90 days as a `List<Object[]>`. The entire dataset is loaded into the JVM heap before processing begins.

2. **`computeUserAffinities()`** (line 246) calls `findUserEventAggregates(since)` which returns **all** aggregated user events for the last 30 days as a `List<Object[]>`.

On a platform designed for millions of concurrent users:
- `findPurchaseEventsForCoPurchase` returns one row per purchase event. Millions of daily purchases × 90 days = potentially hundreds of millions of rows loaded into a single `List`.
- `findUserEventAggregates` aggregates with GROUP BY (reducing row count), but with millions of users × multiple event types × multiple categories, the result can still be millions of rows.
- `computeCoPurchases()` then generates O(n²) product pairs within each purchase window and stores all pair counts in a `HashMap` (lines 63–76), further amplifying memory usage.

Both methods run within a single `@Transactional` with a 300-second timeout (lines 44, 242), holding a database connection for the entire computation. In multi-pod deployments without distributed scheduling locks (no ShedLock or equivalent), **all pods execute simultaneously**, multiplying memory pressure.

Note: The product similarity computation (line 151–153) correctly caps at 500 products, showing awareness of the O(n²) scaling concern. The co-purchase and affinity computations lack similar safeguards.

### Current Code

**`UserEventRepository.java:60–65`** — Returns ALL purchase events:
```java
@Query("""
        SELECT e.userId, e.productId, e.createdAt FROM UserEvent e
        WHERE e.eventType = 'PURCHASE' AND e.createdAt > :since
        ORDER BY e.userId, e.createdAt
        """)
List<Object[]> findPurchaseEventsForCoPurchase(Instant since);
```

**`UserEventRepository.java:76–85`** — Returns ALL user event aggregates:
```java
@Query("""
        SELECT e.userId, e.eventType,
            COALESCE(e.categorySlugs, ''),
            COALESCE(e.brandName, ''),
            COUNT(e)
        FROM UserEvent e
        WHERE e.userId IS NOT NULL AND e.createdAt > :since
        GROUP BY e.userId, e.eventType, COALESCE(e.categorySlugs, ''), COALESCE(e.brandName, '')
        """)
List<Object[]> findUserEventAggregates(Instant since);
```

**`ComputationJobService.java:44–48`**:
```java
@Scheduled(cron = "${personalization.computation.co-purchase-cron:0 0 */6 * * *}")
@Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 300)
public void computeCoPurchases() {
    log.info("Starting co-purchase computation");
    Instant since = Instant.now().minus(90, ChronoUnit.DAYS);
    List<Object[]> purchaseEvents = userEventRepository.findPurchaseEventsForCoPurchase(since);
```

**`ComputationJobService.java:241–246`**:
```java
@Scheduled(cron = "${personalization.computation.affinity-cron:0 0 * * * *}")
@Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 300)
public void computeUserAffinities() {
    log.info("Starting user affinity computation");
    Instant since = Instant.now().minus(30, ChronoUnit.DAYS);
    List<Object[]> aggregates = userEventRepository.findUserEventAggregates(since);
```

### Fix

Add configurable row limits to both repository queries via `Pageable`.

**Step 1** — Add a `Pageable` parameter to `findPurchaseEventsForCoPurchase`:

Replace `UserEventRepository.java:60–65`:
```java
@Query("""
        SELECT e.userId, e.productId, e.createdAt FROM UserEvent e
        WHERE e.eventType = 'PURCHASE' AND e.createdAt > :since
        ORDER BY e.userId, e.createdAt
        """)
List<Object[]> findPurchaseEventsForCoPurchase(Instant since, Pageable pageable);
```

**Step 2** — Add a `Pageable` parameter to `findUserEventAggregates`:

Replace `UserEventRepository.java:76–85`:
```java
@Query("""
        SELECT e.userId, e.eventType,
            COALESCE(e.categorySlugs, ''),
            COALESCE(e.brandName, ''),
            COUNT(e)
        FROM UserEvent e
        WHERE e.userId IS NOT NULL AND e.createdAt > :since
        GROUP BY e.userId, e.eventType, COALESCE(e.categorySlugs, ''), COALESCE(e.brandName, '')
        """)
List<Object[]> findUserEventAggregates(Instant since, Pageable pageable);
```

**Step 3** — Add configurable limit fields to `ComputationJobService`:

Add after `ComputationJobService.java:37`:
```java
@Value("${personalization.computation.co-purchase-max-events:500000}")
private int coPurchaseMaxEvents;

@Value("${personalization.computation.affinity-max-aggregates:500000}")
private int affinityMaxAggregates;
```

**Step 4** — Pass the limit when calling the queries:

Replace `ComputationJobService.java:48`:
```java
List<Object[]> purchaseEvents = userEventRepository.findPurchaseEventsForCoPurchase(
        since, PageRequest.of(0, coPurchaseMaxEvents));
```

Replace `ComputationJobService.java:246`:
```java
List<Object[]> aggregates = userEventRepository.findUserEventAggregates(
        since, PageRequest.of(0, affinityMaxAggregates));
```

Add import:
```java
import org.springframework.data.domain.PageRequest;
```

**Step 5** — Add defaults to `application.yaml` under `personalization.computation`:

```yaml
    co-purchase-max-events: ${PERSONALIZATION_CO_PURCHASE_MAX_EVENTS:500000}
    affinity-max-aggregates: ${PERSONALIZATION_AFFINITY_MAX_AGGREGATES:500000}
```

---

## BUG-PERS-002 — mergeSession() Evicts Entire Recommendations Cache on Every Login

| Field | Value |
|---|---|
| **Severity** | **Medium** |
| **Category** | Architecture & Resilience |
| **Affected Files** | `service/SessionService.java` |
| **Lines** | 27 |

### Description

`SessionService.mergeSession()` is annotated with `@CacheEvict(cacheNames = "recommendations", allEntries = true)`, which evicts **every** entry in the `recommendations` cache whenever **any** user's anonymous session is merged.

Session merges happen on every user login where the user was previously browsing anonymously. On a high-scale platform with thousands of logins per minute, this effectively clears the recommendations cache constantly. The 1-hour TTL configured for the `recommendations` cache becomes meaningless because the cache is wiped before entries can expire naturally.

The merge only affects one specific user's recommendations and one specific anonymous session's recommendations, but `allEntries = true` punishes **all** users by forcing their next recommendation request to hit the database and product-service instead of the cache.

### Current Code

**`SessionService.java:26–28`**:
```java
@Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
@CacheEvict(cacheNames = "recommendations", allEntries = true)
public void mergeSession(UUID userId, String sessionId) {
```

### Fix

Replace the broad `@CacheEvict` with targeted programmatic eviction of only the affected user's and session's cache entries.

**Step 1** — Add `CacheManager` injection:

Replace `SessionService.java:19–24`:
```java
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)
public class SessionService {

    private final AnonymousSessionRepository anonymousSessionRepository;
    private final UserEventRepository userEventRepository;
    private final RecentlyViewedService recentlyViewedService;
    private final CacheManager cacheManager;
```

Add import:
```java
import org.springframework.cache.CacheManager;
import org.springframework.cache.Cache;
```

**Step 2** — Remove `@CacheEvict` and add targeted eviction at end of method:

Replace `SessionService.java:26–51`:
```java
@Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
public void mergeSession(UUID userId, String sessionId) {
    var sessionOpt = anonymousSessionRepository.findBySessionIdAndMergedAtIsNull(sessionId);
    if (sessionOpt.isEmpty()) {
        log.debug("No unmerged anonymous session found for sessionId={}", sessionId);
        return;
    }

    AnonymousSession session = sessionOpt.get();

    int mergedEvents = userEventRepository.mergeSessionEvents(userId, sessionId);
    log.info("Merged {} anonymous events from session {} to user {}", mergedEvents, sessionId, userId);

    try {
        recentlyViewedService.mergeAnonymousToUser(userId, sessionId);
    } catch (Exception e) {
        log.error("Failed to merge recently-viewed for session {}", sessionId, e);
    }

    session.setUserId(userId);
    session.setMergedAt(Instant.now());
    anonymousSessionRepository.save(session);

    evictRecommendationsFor(userId, sessionId);

    log.info("Session {} merged to user {}", sessionId, userId);
}

private void evictRecommendationsFor(UUID userId, String sessionId) {
    Cache cache = cacheManager.getCache("recommendations");
    if (cache == null) return;
    // Evict for all commonly used limit values (controller caps at 100)
    for (int limit : List.of(5, 10, 20, 50, 100)) {
        cache.evict("user::" + userId + "::" + limit);
        cache.evict("anon::" + sessionId + "::" + limit);
    }
}
```

---

## BUG-PERS-003 — Missing @Max Validation on getTrending Limit Parameter

| Field | Value |
|---|---|
| **Severity** | **Medium** |
| **Category** | Logic & Runtime |
| **Affected Files** | `controller/PersonalizationController.java` |
| **Lines** | 57 |

### Description

`PersonalizationController.getTrending()` does not validate the `limit` parameter with `@Max`, unlike every other endpoint in the service that accepts a `limit`:

| Controller | Endpoint | Has `@Max`? |
|---|---|---|
| `PersonalizationController` | `GET /me/recommended` | `@Max(100)` |
| `PersonalizationController` | `GET /me/recently-viewed` | `@Max(100)` |
| `PersonalizationController` | **`GET /trending`** | **Missing** |
| `ProductRecommendationController` | `GET /{id}/similar` | `@Max(100)` |
| `ProductRecommendationController` | `GET /{id}/bought-together` | `@Max(100)` |

A caller can pass `/personalization/trending?limit=999999999`. This:

1. Creates `PageRequest.of(0, 999999999)` in the database query — PostgreSQL returns all matching trending products (potentially hundreds of thousands of rows).
2. Sends all resulting product IDs as a single batch to `ProductClient.getBatchSummaries()` — one massive HTTP request to the product-service.
3. Returns a very large JSON response to the caller.
4. Caches the oversized result in Redis under the key `999999999`, consuming Redis memory.

### Current Code

**`PersonalizationController.java:56–59`**:
```java
@GetMapping("/trending")
public List<ProductSummary> getTrending(@RequestParam(defaultValue = "20") int limit) {
    return trendingService.getTrending(limit);
}
```

### Fix

Add `@Max(100)` consistent with all other limit parameters in the service.

Replace `PersonalizationController.java:56–59`:
```java
@GetMapping("/trending")
public List<ProductSummary> getTrending(@RequestParam(defaultValue = "20") @Max(100) int limit) {
    return trendingService.getTrending(limit);
}
```

---

## BUG-PERS-004 — Retry Wastes Time Retrying Against Open Circuit Breaker

| Field | Value |
|---|---|
| **Severity** | **Low** |
| **Category** | Architecture & Resilience |
| **Affected Files** | `config/CircuitBreakerConfig.java`, `client/ProductClient.java` |
| **Lines** | `CircuitBreakerConfig.java:62–66`, `ProductClient.java:59–67` |

### Description

`ProductClient.call()` wraps API calls in Retry → CircuitBreaker:

```java
var retry = retryRegistry.retry("person-product-client");
return Retry.decorateSupplier(retry, () ->
        circuitBreakerFactory.create("person-product-client")
                .run(action::get, throwable -> {
                    if (throwable instanceof RuntimeException re) throw re;
                    throw new ServiceUnavailableException("Product service unavailable", throwable);
                })
).get();
```

When the circuit breaker is **open**, the Spring Cloud CB interceptor throws `CallNotPermittedException` without invoking the supplier. The fallback re-throws it as a `RuntimeException`. The Retry then catches it and **retries**, because `CallNotPermittedException` is not in the retry's `ignoreExceptions`:

```java
var retryConfig = RetryConfig.custom()
        .maxAttempts(Math.max(1, maxAttempts))
        .waitDuration(Duration.ofMillis(Math.max(100, waitDurationMs)))
        .ignoreExceptions(DownstreamHttpException.class, IllegalArgumentException.class)
        .build();
```

With default config (`maxAttempts=3`, `waitDuration=500ms`), when the CB is open every API call follows this path:

1. Attempt 1: CB open → `CallNotPermittedException` → Retry waits 500ms
2. Attempt 2: CB still open → same → Retry waits 500ms
3. Attempt 3: CB still open → fails after 1000ms of wasted time

This adds **1000ms of unnecessary latency** per request while the product-service is down. The entire purpose of the circuit breaker pattern (fail fast) is defeated by the retry layer.

### Current Code

**`CircuitBreakerConfig.java:62–66`**:
```java
var retryConfig = RetryConfig.custom()
        .maxAttempts(Math.max(1, maxAttempts))
        .waitDuration(Duration.ofMillis(Math.max(100, waitDurationMs)))
        .ignoreExceptions(DownstreamHttpException.class, IllegalArgumentException.class)
        .build();
```

### Fix

Add `CallNotPermittedException` to the retry's `ignoreExceptions` so the retry immediately propagates open-circuit failures.

Replace `CircuitBreakerConfig.java:62–66`:
```java
var retryConfig = RetryConfig.custom()
        .maxAttempts(Math.max(1, maxAttempts))
        .waitDuration(Duration.ofMillis(Math.max(100, waitDurationMs)))
        .ignoreExceptions(
                DownstreamHttpException.class,
                IllegalArgumentException.class,
                io.github.resilience4j.circuitbreaker.CallNotPermittedException.class)
        .build();
```

---

## Summary

| ID | Severity | Category | Title |
|---|---|---|---|
| BUG-PERS-001 | **HIGH** | Architecture & Resilience | Scheduled computation jobs load unbounded data into memory |
| BUG-PERS-002 | Medium | Architecture & Resilience | mergeSession() evicts entire recommendations cache on every login |
| BUG-PERS-003 | Medium | Logic & Runtime | Missing @Max validation on getTrending limit parameter |
| BUG-PERS-004 | Low | Architecture & Resilience | Retry wastes time retrying against open circuit breaker |
