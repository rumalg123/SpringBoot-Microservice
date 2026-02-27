# QA Bugs & Tasks — poster-service

> **Auditor**: Principal Backend QA Architect & Senior Security Bug Hunter
> **Service**: `poster-service` (port 8095)
> **Date**: 2026-02-27
> **Findings**: 4 total — 0 Critical, 1 High, 2 Medium, 1 Low

---

## Domain Map

| Layer | Component | Key Responsibility |
|---|---|---|
| Controller | `AdminPosterController` (`/admin/posters`) | 12 admin endpoints — CRUD, analytics, image upload, A/B variant management |
| Controller | `PosterController` (`/posters`) | 7 public endpoints — list active, get by ID/slug, image serving, click/impression tracking |
| Service | `PosterServiceImpl` | Core poster logic — create/update/soft-delete/restore, cache management, weighted A/B variant selection |
| Service | `SlugUtils` | Static slug normalization utility |
| Storage | `PosterImageStorageServiceImpl` | S3-backed image upload, name generation, and retrieval with validation |
| Repository | `PosterRepository` | Poster CRUD, placement queries, active-in-window queries, atomic click/impression counters |
| Repository | `PosterVariantRepository` | Variant CRUD, batch fetch by poster IDs, atomic click/impression counters |
| Entity | `Poster` | `@Version`, slug (unique), placement, images, link, scheduling window, analytics counters, targeting, soft delete |
| Entity | `PosterVariant` | `@Version`, `@ManyToOne` to Poster, images, weight, analytics counters, active flag |
| Config | `CacheConfig` | Caffeine-based local caches: `postersByPlacement` (max 500) and `posterById` (max 1000), configurable TTL |
| Config | `PosterClickRateLimiter` | Redis-based rate limiting for click/impression endpoints |
| Config | `PosterMutationIdempotencyFilter` | Redis-backed idempotency for admin POST/PUT/DELETE mutations |
| Config | `ObjectStorageConfig` | AWS S3 client for image storage (conditional on `object-storage.enabled`) |
| Config | `SamplePosterDataSeeder` | Seeds sample posters on startup if DB is empty |
| Security | `InternalRequestVerifier` | HMAC-based internal auth with timestamp drift check (60s) |

---

## BUG-POSTER-001 — Rate Limiter Uses Gateway IP Instead of Client IP

| Field | Value |
|---|---|
| **Severity** | **HIGH** |
| **Category** | Logic & Runtime |
| **Affected Files** | `controller/PosterController.java`, `config/PosterClickRateLimiter.java` |
| **Lines** | `PosterController.java:79, 86, 97`, `PosterClickRateLimiter.java:24–31` |

### Description

The `PosterClickRateLimiter` rate-limits click and impression tracking by IP address. However, since this service is only accessed via the API Gateway, `request.getRemoteAddr()` returns the **gateway's IP address**, not the end user's IP.

The Redis key is constructed as `poster:ratelimit:{posterId}:{ip}`. With the gateway's IP, **all users share a single counter per poster**. With a default limit of 10 per minute, the 11th click/impression from **any user** on a given poster silently drops tracking for **all users** for the remainder of that minute.

On a high-traffic platform, this means:

1. **Click/impression analytics are massively undercounted** — only the first 10 events per poster per minute are recorded.
2. **A/B variant click tracking is similarly affected** — `recordVariantClick` also uses the rate limiter.
3. **The drop is silent** — the endpoint returns 204 regardless, so there is no indication that data is being lost.

### Current Code

**`PosterController.java:76–88`** — Passes `getRemoteAddr()` to rate limiter:
```java
@PostMapping("/{id}/click")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void recordClick(@PathVariable UUID id, HttpServletRequest request) {
    if (!rateLimiter.isAllowed(request.getRemoteAddr(), id)) return;
    posterService.recordClick(id);
}

@PostMapping("/{id}/impression")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void recordImpression(@PathVariable UUID id, HttpServletRequest request) {
    if (!rateLimiter.isAllowed(request.getRemoteAddr(), id)) return;
    posterService.recordImpression(id);
}
```

**`PosterClickRateLimiter.java:24–31`** — Uses raw IP as key component:
```java
public boolean isAllowed(String ip, UUID posterId) {
    String key = "poster:ratelimit:" + posterId + ":" + ip;
    try {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, Duration.ofMinutes(1));
        }
        return count != null && count <= maxClicksPerMinute;
    } catch (Exception e) {
        return true;
    }
}
```

### Fix

**Step 1** — Add a helper to extract the real client IP in `PosterClickRateLimiter`:

Replace `PosterClickRateLimiter.java:24–31`:
```java
public boolean isAllowed(HttpServletRequest request, UUID posterId) {
    String ip = resolveClientIp(request);
    String key = "poster:ratelimit:" + posterId + ":" + ip;
    try {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, Duration.ofMinutes(1));
        }
        return count != null && count <= maxClicksPerMinute;
    } catch (Exception e) {
        return true;
    }
}

private String resolveClientIp(HttpServletRequest request) {
    String xff = request.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) {
        String first = xff.split(",")[0].trim();
        if (!first.isEmpty()) {
            return first;
        }
    }
    return request.getRemoteAddr();
}
```

Add the import at the top of `PosterClickRateLimiter.java`:
```java
import jakarta.servlet.http.HttpServletRequest;
```

**Step 2** — Update controller calls to pass the full `HttpServletRequest`:

Replace `PosterController.java:78–81`:
```java
public void recordClick(@PathVariable UUID id, HttpServletRequest request) {
    if (!rateLimiter.isAllowed(request, id)) return;
    posterService.recordClick(id);
}
```

Replace `PosterController.java:85–88`:
```java
public void recordImpression(@PathVariable UUID id, HttpServletRequest request) {
    if (!rateLimiter.isAllowed(request, id)) return;
    posterService.recordImpression(id);
}
```

Replace `PosterController.java:96–99`:
```java
public void recordVariantClick(..., HttpServletRequest request) {
    if (!rateLimiter.isAllowed(request, posterId)) return;
    posterService.recordVariantClick(posterId, variantId);
}
```

---

## BUG-POSTER-002 — Variant Analytics Broken — Missing Impression Tracking and Parent Click Propagation

| Field | Value |
|---|---|
| **Severity** | **Medium** |
| **Category** | Logic & Runtime |
| **Affected Files** | `service/PosterServiceImpl.java`, `controller/PosterController.java` |
| **Lines** | `PosterServiceImpl.java:428–431, 545–555`, `PosterController.java:90–99` |

### Description

The A/B testing variant system has two analytics defects:

**1. Variant clicks don't propagate to the parent poster.**

`recordVariantClick()` increments only `PosterVariant.clicks` via `posterVariantRepository.incrementClickCount(variantId)`. The parent `Poster.clickCount` is **not** incremented. A variant click **is** a click on the poster, so poster-level analytics undercount clicks whenever users interact through variant-served content.

**2. Variant impressions are never tracked.**

`PosterVariantRepository.incrementImpressionCount()` exists (line 26–27) but is **never called** from any service or controller. There is no `recordVariantImpression` endpoint. The `PosterVariant.impressions` field stays at 0 forever.

This means `toVariantResponse()` always computes CTR as 0.0:

```java
double ctr = v.getImpressions() > 0
        ? (double) v.getClicks() / v.getImpressions()
        : 0.0;
```

The entire variant-level click-through-rate metric is non-functional, making A/B test performance comparison impossible.

### Current Code

**`PosterServiceImpl.java:545–555`** — Only increments variant click, not poster:
```java
@Override
@Transactional(readOnly = false, timeout = 5)
public void recordVariantClick(UUID posterId, UUID variantId) {
    boolean variantExists = posterVariantRepository.findById(variantId)
            .filter(v -> v.getPoster().getId().equals(posterId))
            .isPresent();
    if (!variantExists) {
        throw new ResourceNotFoundException("Variant not found: " + variantId);
    }
    posterVariantRepository.incrementClickCount(variantId);
}
```

**`PosterController.java:90–99`** — No variant impression endpoint exists:
```java
@PostMapping("/{posterId}/variants/{variantId}/click")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void recordVariantClick(
        @PathVariable UUID posterId,
        @PathVariable UUID variantId,
        HttpServletRequest request
) {
    if (!rateLimiter.isAllowed(request.getRemoteAddr(), posterId)) return;
    posterService.recordVariantClick(posterId, variantId);
}
```

### Fix

**Step 1** — In `recordVariantClick()`, also increment the parent poster's click count:

Replace `PosterServiceImpl.java:545–555`:
```java
@Override
@Transactional(readOnly = false, timeout = 5)
public void recordVariantClick(UUID posterId, UUID variantId) {
    boolean variantExists = posterVariantRepository.findById(variantId)
            .filter(v -> v.getPoster().getId().equals(posterId))
            .isPresent();
    if (!variantExists) {
        throw new ResourceNotFoundException("Variant not found: " + variantId);
    }
    posterVariantRepository.incrementClickCount(variantId);
    posterRepository.incrementClickCount(posterId, Instant.now());
}
```

**Step 2** — Add `recordVariantImpression` method to `PosterService` interface:

Add after `recordVariantClick` declaration in `PosterService.java` (~line 40):
```java
void recordVariantImpression(UUID posterId, UUID variantId);
```

**Step 3** — Implement `recordVariantImpression` in `PosterServiceImpl`:

Add after `recordVariantClick` method (~line 555):
```java
@Override
@Transactional(readOnly = false, timeout = 5)
public void recordVariantImpression(UUID posterId, UUID variantId) {
    boolean variantExists = posterVariantRepository.findById(variantId)
            .filter(v -> v.getPoster().getId().equals(posterId))
            .isPresent();
    if (!variantExists) {
        throw new ResourceNotFoundException("Variant not found: " + variantId);
    }
    posterVariantRepository.incrementImpressionCount(variantId);
    posterRepository.incrementImpressionCount(posterId, Instant.now());
}
```

**Step 4** — Add the endpoint in `PosterController`:

Add after the `recordVariantClick` endpoint (~line 99):
```java
@PostMapping("/{posterId}/variants/{variantId}/impression")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void recordVariantImpression(
        @PathVariable UUID posterId,
        @PathVariable UUID variantId,
        HttpServletRequest request
) {
    if (!rateLimiter.isAllowed(request.getRemoteAddr(), posterId)) return;
    posterService.recordVariantImpression(posterId, variantId);
}
```

---

## BUG-POSTER-003 — `listDeleted(Pageable)` Has N+1 Variant Query Problem

| Field | Value |
|---|---|
| **Severity** | **Medium** |
| **Category** | Data Integrity & Concurrency |
| **Affected Files** | `service/PosterServiceImpl.java` |
| **Lines** | 166–168 |

### Description

The paginated `listDeleted(Pageable)` method uses `this::toResponse` which calls the single-poster `toResponse(Poster p)` overload. This overload executes an individual `posterVariantRepository.findByPosterIdOrderByCreatedAtAsc(p.getId())` query for **each poster** in the page — a classic N+1 problem.

All other paginated list methods in the same class correctly use `batchFetchVariants(page.getContent())` to load all variants in a single query:

| Method | Uses Batch Fetch? |
|---|---|
| `listAllNonDeleted(Pageable)` (line 136) | Yes |
| `listActiveByPlacement(placement, Pageable)` (line 116) | Yes |
| `listAllActive(Pageable)` (line 123) | Yes |
| `listDeleted()` (line 143, no pageable) | Yes |
| **`listDeleted(Pageable)`** (line 166) | **No — N+1** |

The `AdminPosterController.listDeleted()` endpoint calls this paginated version. With a page size of 20 (default), each request triggers 1 + 20 = 21 queries instead of 2.

### Current Code

**`PosterServiceImpl.java:166–168`**:
```java
@Override
public Page<PosterResponse> listDeleted(Pageable pageable) {
    return posterRepository.findByDeletedTrueOrderByUpdatedAtDesc(pageable).map(this::toResponse);
}
```

**`PosterServiceImpl.java:328–330`** — The single-poster `toResponse` triggers individual query:
```java
private PosterResponse toResponse(Poster p) {
    return toResponse(p, posterVariantRepository.findByPosterIdOrderByCreatedAtAsc(p.getId()));
}
```

### Fix

Replace `PosterServiceImpl.java:166–168`:
```java
@Override
public Page<PosterResponse> listDeleted(Pageable pageable) {
    Page<Poster> page = posterRepository.findByDeletedTrueOrderByUpdatedAtDesc(pageable);
    Map<UUID, List<PosterVariant>> variantsByPoster = batchFetchVariants(page.getContent());
    return page.map(p -> toResponse(p, variantsByPoster.getOrDefault(p.getId(), List.of())));
}
```

---

## BUG-POSTER-004 — Cached `getByIdOrSlug` Freezes A/B Variant Selection for Cache TTL

| Field | Value |
|---|---|
| **Severity** | **Low** |
| **Category** | Architecture & Resilience |
| **Affected Files** | `service/PosterServiceImpl.java` |
| **Lines** | 69, 328–336, 409–426 |

### Description

The `getByIdOrSlug()` method is `@Cacheable` and caches the entire `PosterResponse` including the randomly-selected `selectedVariant`. Since `selectWeightedVariant()` uses `ThreadLocalRandom` to pick a variant, the **first request's randomly-chosen variant is frozen in cache** for the TTL (default 15 seconds).

During that 15-second window, **every user** retrieving that poster via `GET /posters/{idOrSlug}` sees the same A/B variant. Instead of continuous per-request randomization, variant distribution happens in discrete 15-second time blocks. Over a long enough period, the statistical distribution will approximate the configured weights, but short A/B tests or tests with few impressions will produce skewed results.

### Current Code

**`PosterServiceImpl.java:69`** — Caches full response:
```java
@Cacheable(cacheNames = "posterById", key = "'any::' + #idOrSlug")
public PosterResponse getByIdOrSlug(String idOrSlug) {
```

**`PosterServiceImpl.java:332–336`** — Variant selection happens inside cached method:
```java
private PosterResponse toResponse(Poster p, List<PosterVariant> allVariantEntities) {
    List<PosterVariant> activeVariants = allVariantEntities.stream()
            .filter(PosterVariant::isActive)
            .toList();
    PosterVariantResponse selectedVariant = selectWeightedVariant(activeVariants);
```

**`PosterServiceImpl.java:409–426`** — Random selection:
```java
private PosterVariantResponse selectWeightedVariant(List<PosterVariant> activeVariants) {
    ...
    int random = ThreadLocalRandom.current().nextInt(totalWeight);
    ...
}
```

### Fix

Separate the cached poster data from per-request variant selection. Cache the poster with its full variant list but perform variant selection **after** cache retrieval.

Replace `PosterServiceImpl.java:68–89`:
```java
@Override
public PosterResponse getByIdOrSlug(String idOrSlug) {
    PosterResponse cached = getCachedPosterByIdOrSlug(idOrSlug);
    if (cached.variants() == null || cached.variants().isEmpty()) {
        return cached;
    }
    List<PosterVariantResponse> activeVariants = cached.variants().stream()
            .filter(PosterVariantResponse::active)
            .toList();
    PosterVariantResponse selected = selectWeightedVariantResponse(activeVariants);
    return new PosterResponse(
            cached.id(), cached.name(), cached.slug(), cached.placement(), cached.size(),
            selected != null && cached.desktopImage() != null ? applyVariantImage(selected.desktopImage(), cached.desktopImage()) : cached.desktopImage(),
            selected != null ? applyVariantImage(selected.mobileImage(), cached.mobileImage()) : cached.mobileImage(),
            selected != null ? applyVariantImage(selected.tabletImage(), cached.tabletImage()) : cached.tabletImage(),
            selected != null ? applyVariantImage(selected.srcsetDesktop(), cached.srcsetDesktop()) : cached.srcsetDesktop(),
            selected != null ? applyVariantImage(selected.srcsetMobile(), cached.srcsetMobile()) : cached.srcsetMobile(),
            selected != null ? applyVariantImage(selected.srcsetTablet(), cached.srcsetTablet()) : cached.srcsetTablet(),
            cached.linkType(), cached.linkTarget(), cached.openInNewTab(),
            cached.title(), cached.subtitle(), cached.ctaLabel(), cached.backgroundColor(),
            cached.sortOrder(), cached.active(), cached.startAt(), cached.endAt(),
            cached.clickCount(), cached.impressionCount(), cached.lastClickAt(), cached.lastImpressionAt(),
            cached.targetCountries(), cached.targetCustomerSegment(),
            cached.deleted(), cached.deletedAt(), cached.createdAt(), cached.updatedAt(),
            selected, cached.variants()
    );
}

@Cacheable(cacheNames = "posterById", key = "'any::' + #idOrSlug")
public PosterResponse getCachedPosterByIdOrSlug(String idOrSlug) {
    UUID parsed = tryParseUuid(idOrSlug);
    Poster poster;
    if (parsed != null) {
        poster = posterRepository.findById(parsed)
                .orElseThrow(() -> new ResourceNotFoundException("Poster not found: " + idOrSlug));
    } else {
        String normalizedSlug = normalizeRequestedSlug(idOrSlug);
        poster = posterRepository.findBySlug(normalizedSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Poster not found: " + idOrSlug));
    }
    if (poster.isDeleted()) {
        throw new ResourceNotFoundException("Poster not found: " + idOrSlug);
    }
    Instant now = Instant.now();
    if (!poster.isActive() || !isActiveInWindow(poster, now)) {
        throw new ResourceNotFoundException("Poster not found: " + idOrSlug);
    }
    // Build response with null selectedVariant — variant selection happens post-cache
    List<PosterVariant> allVariants = posterVariantRepository.findByPosterIdOrderByCreatedAtAsc(poster.getId());
    List<PosterVariantResponse> variantResponses = allVariants.stream().map(this::toVariantResponse).toList();
    return new PosterResponse(
            poster.getId(), poster.getName(), poster.getSlug(), poster.getPlacement(), poster.getSize(),
            poster.getDesktopImage(), poster.getMobileImage(), poster.getTabletImage(),
            poster.getSrcsetDesktop(), poster.getSrcsetMobile(), poster.getSrcsetTablet(),
            poster.getLinkType(), poster.getLinkTarget(), poster.isOpenInNewTab(),
            poster.getTitle(), poster.getSubtitle(), poster.getCtaLabel(), poster.getBackgroundColor(),
            poster.getSortOrder(), poster.isActive(), poster.getStartAt(), poster.getEndAt(),
            poster.getClickCount(), poster.getImpressionCount(), poster.getLastClickAt(), poster.getLastImpressionAt(),
            poster.getTargetCountries() == null ? java.util.Set.of() : java.util.Set.copyOf(poster.getTargetCountries()),
            poster.getTargetCustomerSegment(),
            poster.isDeleted(), poster.getDeletedAt(), poster.getCreatedAt(), poster.getUpdatedAt(),
            null, variantResponses
    );
}

private String applyVariantImage(String variantImage, String fallback) {
    return StringUtils.hasText(variantImage) ? variantImage : fallback;
}

private PosterVariantResponse selectWeightedVariantResponse(List<PosterVariantResponse> activeVariants) {
    if (activeVariants == null || activeVariants.isEmpty()) {
        return null;
    }
    int totalWeight = activeVariants.stream().mapToInt(PosterVariantResponse::weight).sum();
    if (totalWeight <= 0) {
        return activeVariants.getFirst();
    }
    int random = ThreadLocalRandom.current().nextInt(totalWeight);
    int cumulative = 0;
    for (PosterVariantResponse variant : activeVariants) {
        cumulative += variant.weight();
        if (random < cumulative) {
            return variant;
        }
    }
    return activeVariants.getLast();
}
```

**Note**: `getCachedPosterByIdOrSlug` must also be added to the `PosterService` interface for the Spring proxy to intercept the `@Cacheable` annotation.

---

## Summary

| ID | Severity | Category | Title |
|---|---|---|---|
| BUG-POSTER-001 | **HIGH** | Logic & Runtime | Rate limiter uses gateway IP — silently drops most click/impression analytics |
| BUG-POSTER-002 | Medium | Logic & Runtime | Variant analytics broken — no impression tracking, variant clicks don't propagate to parent |
| BUG-POSTER-003 | Medium | Data Integrity & Concurrency | `listDeleted(Pageable)` N+1 variant queries |
| BUG-POSTER-004 | Low | Architecture & Resilience | Cached `getByIdOrSlug` freezes A/B variant selection for cache TTL |
