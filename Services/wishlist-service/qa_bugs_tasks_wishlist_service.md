# QA Bugs & Tasks — wishlist-service

> **Auditor**: Principal Backend QA Architect & Senior Security Bug Hunter
> **Service**: `wishlist-service` (port 8087)
> **Date**: 2026-02-27
> **Findings**: 4 total — 1 High, 1 Medium, 2 Low

---

## Domain Map

| Layer | Component | Key Responsibility |
|---|---|---|
| Controller | `WishlistController` (`/wishlist`) | Customer-facing: legacy flat wishlist (CRUD items), collection management (CRUD), sharing (enable/revoke/get shared), move-to-cart, item notes |
| Controller | `InternalWishlistAnalyticsController` (`/internal/wishlist/analytics`) | Internal: platform summary, most wished products |
| Service | `WishlistService` | Core wishlist logic — items, collections, sharing, move-to-cart, default collection auto-creation |
| Service | `WishlistAnalyticsService` | Platform summary, most wished products |
| Repository | `WishlistCollectionRepository` | Collection CRUD, keycloakId queries, share token lookup |
| Repository | `WishlistItemRepository` | Item CRUD, batch queries by collection IDs, analytics aggregates |
| Entity | `WishlistCollection` | `@Version`, keycloakId, name, `isDefault`, `shared`, `shareToken` (unique), no unique constraint on `(keycloakId, isDefault)` |
| Entity | `WishlistItem` | `@Version`, keycloakId, `@ManyToOne` collection, productId, product snapshot fields, UK on `(collection_id, product_id)` |
| Client | `ProductClient` | Fetch product details (CB + Retry AOP: productService) |
| Client | `CartClient` | Add item to cart during move-to-cart (CB + Retry AOP: cartService) |
| Config | `CacheConfig` | Redis cache: `wishlistByKeycloak` (45s TTL, default 30s) |
| Config | `HttpClientConfig` | Apache HttpClient5 pooling (100 max, 20 per route) |
| Config | `PaginationConfig` | Max page size 100 |
| Config | `WishlistMutationIdempotencyFilter` | Redis-backed idempotency for POST/PUT/DELETE mutations |
| Security | `InternalRequestVerifier` | HMAC-based internal auth for admin/internal endpoints |

---

## BUG-WISHLIST-001 — Duplicate Default Collection Race Condition Causes Permanent 500 Errors

| Field | Value |
|---|---|
| **Severity** | **HIGH** |
| **Category** | Data Integrity & Concurrency |
| **Affected Files** | `service/WishlistService.java`, `entity/WishlistCollection.java`, `repo/WishlistCollectionRepository.java` |
| **Lines** | `WishlistService.java:353–370`, `WishlistCollection.java:23–32`, `WishlistCollectionRepository.java:16` |

### Description

The `getOrCreateDefaultCollection()` method uses a check-then-act pattern to create a default collection for first-time users:

```java
private WishlistCollection getOrCreateDefaultCollection(String keycloakId) {
    return wishlistCollectionRepository.findByKeycloakIdAndIsDefaultTrue(keycloakId)
            .orElseGet(() -> {
                try {
                    WishlistCollection defaultCollection = WishlistCollection.builder()
                            .keycloakId(keycloakId)
                            .name("My Wishlist")
                            .isDefault(true)
                            .build();
                    return wishlistCollectionRepository.save(defaultCollection);
                } catch (org.springframework.dao.DataIntegrityViolationException e) {
                    // Concurrent creation race — re-fetch the one that won
                    return wishlistCollectionRepository.findByKeycloakIdAndIsDefaultTrue(keycloakId)
                            .orElseThrow(() -> new IllegalStateException(
                                    "Default collection not found after concurrent creation", e));
                }
            });
}
```

The `DataIntegrityViolationException` catch block is **dead code** because there is **no unique constraint** on `(keycloak_id, is_default)`. The entity only has:

- `@UniqueConstraint` on `share_token`
- `@Index` (not unique) on `(keycloak_id, is_default)`

**Race condition flow:**

1. User's first-ever `addItem()` call arrives concurrently (e.g., double-click, parallel frontend requests)
2. Both requests call `addItem()` → `resolveCollection(null)` → `getOrCreateDefaultCollection()`
3. Both find no default collection via `findByKeycloakIdAndIsDefaultTrue()` (READ_COMMITTED — both see empty)
4. Both create and save a default collection. **No `DataIntegrityViolationException`** is thrown because no unique constraint exists
5. User now has **two default collections** in the database

**Permanent failure after the race:**

Every subsequent call to `getOrCreateDefaultCollection()` calls `findByKeycloakIdAndIsDefaultTrue()` which returns `Optional<WishlistCollection>`. With two rows matching, Spring Data JPA throws `IncorrectResultSizeDataAccessException`, which falls to the catch-all 500 handler. The user's wishlist is **permanently broken** — every `addItem()` call without an explicit `collectionId` fails with 500 until manual database cleanup.

### Fix

**Step 1** — Change the repository to return a `List` instead of `Optional` to prevent `IncorrectResultSizeDataAccessException`.

Replace `WishlistCollectionRepository.java:16`:
```java
Optional<WishlistCollection> findByKeycloakIdAndIsDefaultTrue(String keycloakId);
```

With:
```java
List<WishlistCollection> findByKeycloakIdAndIsDefaultTrueOrderByCreatedAtAsc(String keycloakId);
```

**Step 2** — Update `getOrCreateDefaultCollection()` to use the list-based query and handle duplicates gracefully.

Replace `WishlistService.java:353–370`:
```java
private WishlistCollection getOrCreateDefaultCollection(String keycloakId) {
    List<WishlistCollection> defaults = wishlistCollectionRepository
            .findByKeycloakIdAndIsDefaultTrueOrderByCreatedAtAsc(keycloakId);
    if (!defaults.isEmpty()) {
        return defaults.getFirst();
    }
    try {
        WishlistCollection defaultCollection = WishlistCollection.builder()
                .keycloakId(keycloakId)
                .name("My Wishlist")
                .isDefault(true)
                .build();
        return wishlistCollectionRepository.save(defaultCollection);
    } catch (org.springframework.dao.DataIntegrityViolationException e) {
        // Concurrent creation race — re-fetch the one that won
        List<WishlistCollection> retryDefaults = wishlistCollectionRepository
                .findByKeycloakIdAndIsDefaultTrueOrderByCreatedAtAsc(keycloakId);
        if (!retryDefaults.isEmpty()) {
            return retryDefaults.getFirst();
        }
        throw new IllegalStateException("Default collection not found after concurrent creation", e);
    }
}
```

**Step 3** — Add a partial unique index to prevent future duplicates. Add to `WishlistCollection.java` after the existing `@Table` annotation, or via a database migration:

```sql
CREATE UNIQUE INDEX uk_collection_keycloak_default
    ON wishlist_collections (keycloak_id) WHERE is_default = true;
```

Since JPA annotations don't support partial unique indexes natively, use Hibernate's `@org.hibernate.annotations.SQLRestriction` or a schema initialization script (`schema.sql`):

```java
// schema.sql (or Flyway/Liquibase migration)
CREATE UNIQUE INDEX IF NOT EXISTS uk_collection_keycloak_default
    ON wishlist_collections (keycloak_id) WHERE is_default = true;
```

With the partial unique index in place, the `DataIntegrityViolationException` catch block becomes functional, and the List-based query provides safety even if the index is somehow missing.

---

## BUG-WISHLIST-002 — Shared Wishlist Exposes Personal Item Notes

| Field | Value |
|---|---|
| **Severity** | **Medium** |
| **Category** | Security & Access Control |
| **Affected Files** | `service/WishlistService.java` |
| **Lines** | `WishlistService.java:276–298, 422–436` |

### Description

When a user shares a wishlist collection via `GET /wishlist/shared/{shareToken}`, the response includes `toItemResponse()` for each item, which exposes the `note` field:

```java
public SharedWishlistResponse getSharedWishlist(String shareToken) {
    // ...
    List<WishlistItem> items = wishlistItemRepository.findByCollectionOrderByCreatedAtDesc(collection);
    List<WishlistItemResponse> itemResponses = items.stream()
            .map(this::toItemResponse)  // <-- Includes note
            .toList();
    // ...
}
```

```java
private WishlistItemResponse toItemResponse(WishlistItem item) {
    return new WishlistItemResponse(
            item.getId(),
            item.getCollection() != null ? item.getCollection().getId() : null,
            item.getProductId(),
            item.getProductSlug(),
            item.getProductName(),
            item.getProductType(),
            item.getMainImage(),
            normalizeMoney(item.getSellingPriceSnapshot()),
            item.getNote(),       // <-- Personal notes exposed
            item.getCreatedAt(),
            item.getUpdatedAt()
    );
}
```

The `note` field (up to 500 characters, set via `PUT /wishlist/me/items/{itemId}/note`) can contain private, sensitive text such as:
- "Anniversary gift for Sarah — budget max $200"
- "Don't buy if I already got this from Mom"
- "Salary bonus wish — don't share with coworkers"

Anyone with the share link sees all personal notes. The share token is a 32-character hex string (UUID without dashes), but once shared (e.g., via social media, messaging), any recipient can view the notes.

### Fix

Create a separate DTO for shared wishlist items that excludes the `note` field.

**Step 1** — Create a new DTO for shared item responses.

Create `dto/SharedWishlistItemResponse.java`:
```java
package com.rumal.wishlist_service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SharedWishlistItemResponse(
        UUID productId,
        String productSlug,
        String productName,
        String productType,
        String mainImage,
        BigDecimal sellingPriceSnapshot,
        Instant createdAt
) {}
```

**Step 2** — Update `SharedWishlistResponse` to use the new DTO.

Replace `SharedWishlistResponse.java`:
```java
package com.rumal.wishlist_service.dto;

import java.util.List;

public record SharedWishlistResponse(
        String collectionName,
        String description,
        List<SharedWishlistItemResponse> items,
        int itemCount
) {}
```

**Step 3** — Add a mapping method in `WishlistService.java`. Add after `toItemResponse` (~line 436):

```java
private SharedWishlistItemResponse toSharedItemResponse(WishlistItem item) {
    return new SharedWishlistItemResponse(
            item.getProductId(),
            item.getProductSlug(),
            item.getProductName(),
            item.getProductType(),
            item.getMainImage(),
            normalizeMoney(item.getSellingPriceSnapshot()),
            item.getCreatedAt()
    );
}
```

**Step 4** — Update `getSharedWishlist()` to use the new mapping.

Replace `WishlistService.java:287–297`:
```java
List<WishlistItem> items = wishlistItemRepository.findByCollectionOrderByCreatedAtDesc(collection);
List<SharedWishlistItemResponse> itemResponses = items.stream()
        .map(this::toSharedItemResponse)
        .toList();

return new SharedWishlistResponse(
        collection.getName(),
        collection.getDescription(),
        itemResponses,
        itemResponses.size()
);
```

This also removes the internal `item.id` and `collection.id` from the shared response, which is a defense-in-depth improvement.

---

## BUG-WISHLIST-003 — Resilience4j Retries Broken for Both Clients

| Field | Value |
|---|---|
| **Severity** | **Low** |
| **Category** | Architecture & Resilience |
| **Affected Files** | `client/ProductClient.java`, `client/CartClient.java`, `application.yaml` |
| **Lines** | `ProductClient.java:43–44`, `CartClient.java:52–53`, `application.yaml:70–82` |

### Description

The Resilience4j retry configuration specifies retryable exceptions:

```yaml
retryExceptions:
  - java.io.IOException
  - org.springframework.web.client.ResourceAccessException
  - org.springframework.web.client.HttpServerErrorException
```

However, both clients catch `RestClientException` (the parent of both `ResourceAccessException` and `HttpServerErrorException`) and **re-wrap** it as `ServiceUnavailableException` **before** the exception reaches the Retry AOP aspect:

```java
// ProductClient.java:43–44
} catch (RestClientException ex) {
    throw new ServiceUnavailableException("Service unavailable: " + ex.getMessage());
}
```

```java
// CartClient.java:52–53
} catch (RestClientException ex) {
    throw new ServiceUnavailableException("Cart service unavailable: " + ex.getMessage());
}
```

Since `ServiceUnavailableException` is **not** in the `retryExceptions` list, the Retry aspect sees it and does **not** retry. Transient connectivity failures and 5xx responses immediately surface as errors with zero retries.

**Impact on `moveItemToCart()`**: If cart-service has a transient failure, no retry occurs. The item stays in the wishlist, which is the safe outcome, but the user must manually retry the operation.

**Impact on `addItem()`**: If product-service has a transient failure when resolving product details, no retry occurs. The user's add-to-wishlist request fails immediately.

### Fix

Add `ServiceUnavailableException` to the retry configuration for both instances.

**`application.yaml`** — Replace the retry configuration for `productService` (lines 65–73):
```yaml
      productService:
        maxAttempts: 3
        waitDuration: 300ms
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
        retryExceptions:
          - java.io.IOException
          - org.springframework.web.client.ResourceAccessException
          - org.springframework.web.client.HttpServerErrorException
          - com.rumal.wishlist_service.exception.ServiceUnavailableException
```

Apply the same change to `cartService` (lines 74–82).

---

## BUG-WISHLIST-004 — Missing OptimisticLockingFailureException Handler Returns 500 Instead of 409

| Field | Value |
|---|---|
| **Severity** | **Low** |
| **Category** | Data Integrity & Concurrency |
| **Affected Files** | `exception/GlobalExceptionHandler.java` |
| **Lines** | `GlobalExceptionHandler.java:59–68` |

### Description

Both `WishlistCollection` and `WishlistItem` entities use `@Version` for optimistic locking. When two concurrent modifications hit the same entity (e.g., two concurrent `updateItemNote` calls on the same item), Hibernate throws `OptimisticLockingFailureException`.

The `GlobalExceptionHandler` does **not** have an explicit handler for this exception. It falls through to the catch-all:

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
    log.error("Unexpected error", ex);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("timestamp", Instant.now());
    body.put("status", 500);
    body.put("error", "Unexpected error");
    body.put("message", "Unexpected error");
    return ResponseEntity.status(500).body(body);
}
```

The client receives a **500 "Unexpected error"** instead of a meaningful **409 Conflict** response indicating a concurrent modification, preventing the client from implementing a simple retry strategy.

### Fix

Add an explicit handler for `OptimisticLockingFailureException` in `GlobalExceptionHandler.java`.

Add after `badRequest` handler (~line 43):
```java
@ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
public ResponseEntity<?> handleOptimisticLock(org.springframework.orm.ObjectOptimisticLockingFailureException ex) {
    log.warn("Wishlist optimistic lock conflict: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(error(HttpStatus.CONFLICT, "Concurrent modification detected. Please retry."));
}
```

---

## Summary

| ID | Severity | Category | Title |
|---|---|---|---|
| BUG-WISHLIST-001 | **HIGH** | Data Integrity & Concurrency | Duplicate default collection race condition — no unique constraint on `(keycloak_id, is_default)`, causes permanent 500 errors |
| BUG-WISHLIST-002 | Medium | Security & Access Control | Shared wishlist exposes personal item notes to anyone with share link |
| BUG-WISHLIST-003 | Low | Architecture & Resilience | Resilience4j retries broken for both clients — `ServiceUnavailableException` not in retryExceptions |
| BUG-WISHLIST-004 | Low | Data Integrity & Concurrency | Missing `OptimisticLockingFailureException` handler — returns 500 instead of 409 Conflict |
