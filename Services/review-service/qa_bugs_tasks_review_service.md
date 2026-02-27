# QA Bugs & Tasks — review-service

> **Auditor**: Principal Backend QA Architect & Senior Security Bug Hunter
> **Service**: `review-service` (port 8086)
> **Date**: 2026-02-27
> **Findings**: 4 total — 0 Critical, 1 High, 2 Medium, 1 Low

---

## Domain Map

| Layer | Component | Key Responsibility |
|---|---|---|
| Controller | `MyReviewController` (`/reviews/me`) | Customer-facing: create/update/delete review, vote, report |
| Controller | `VendorReviewController` (`/reviews/vendor`) | Vendor-facing: list reviews, create/update/delete reply |
| Controller | `AdminReviewController` (`/admin/reviews`) | Platform admin: list/moderate reviews, manage reports |
| Controller | `PublicReviewController` (`/reviews`) | Public storefront: list by product, summary, image serving |
| Controller | `ReviewImageController` (`/reviews/me/images`) | Customer image upload to S3 |
| Controller | `InternalReviewController` (`/internal/reviews`) | Inter-service: product review summary |
| Controller | `InternalReviewAnalyticsController` (`/internal/reviews/analytics`) | Inter-service: platform/vendor analytics |
| Service | `ReviewServiceImpl` | Core review CRUD, voting, reporting, vendor replies |
| Service | `ReviewCacheVersionService` | Redis-backed cache version bumping for targeted invalidation |
| Service | `ReviewAnalyticsService` | Platform and vendor-level review analytics |
| Service | `ReviewImageStorageServiceImpl` | S3 image upload/delete/get with validation + thumbnail generation |
| Repository | `ReviewRepository` | Review CRUD, specification queries, atomic count updates, analytics |
| Repository | `ReviewVoteRepository` | Vote CRUD, count queries |
| Repository | `ReviewReportRepository` | Report CRUD, specification queries |
| Repository | `VendorReplyRepository` | Vendor reply CRUD, analytics count |
| Entity | `Review` | `@Version`, customerId, productId, vendorId, orderId, rating, `@ElementCollection` images, `@OneToOne` VendorReply |
| Entity | `ReviewVote` | `UK(review_id, user_id)`, helpful boolean |
| Entity | `VendorReply` | `@Version`, `@OneToOne` Review (unique FK), vendorId, comment |
| Entity | `ReviewReport` | `@Version`, reason enum, status (PENDING/REVIEWED/DISMISSED), adminNotes |
| Client | `CustomerClient` | Fetch customer by keycloakId (CB: customerService) |
| Client | `OrderPurchaseVerificationClient` | Verify customer purchased product (CB: orderService) |
| Client | `VendorClient` | Resolve keycloakSub to vendorId (CB: vendorService) |
| Filter | `ReviewMutationIdempotencyFilter` | Redis-backed idempotency for POST/PUT/DELETE mutations |

---

## BUG-REVIEW-001 — Duplicate Review Race Condition (No Unique Constraint)

| Field | Value |
|---|---|
| **Severity** | **HIGH** |
| **Category** | Data Integrity & Concurrency |
| **Affected Files** | `service/ReviewServiceImpl.java`, `entity/Review.java` |
| **Lines** | `ReviewServiceImpl.java:47–49` |

### Description

The `createReview()` method uses a check-then-act pattern to prevent duplicate reviews:

```java
if (reviewRepository.existsByCustomerIdAndProductIdAndDeletedFalse(customerId, request.productId())) {
    throw new ValidationException("You have already reviewed this product");
}
```

This check is a **TOCTOU (Time-of-Check to Time-of-Use)** vulnerability. The `Review` entity has an index on `(customer_id, product_id)` but **not a unique constraint**:

```java
@Index(name = "idx_reviews_customer_product", columnList = "customer_id, product_id")
```

Under `REPEATABLE_READ` isolation, two concurrent transactions can both read that no review exists (each sees a snapshot as of their transaction start), then both insert a new review. PostgreSQL's REPEATABLE_READ does **not** detect this as a serialization anomaly because neither transaction modified a row that the other read — they both read "no rows" and both inserted new rows.

The Redis idempotency filter only deduplicates requests with the **same** `Idempotency-Key`. Two requests with different keys (e.g., browser retry vs. double-click with regenerated key) both pass through.

**Result**: A customer can end up with multiple reviews for the same product.

### Current Code

**`ReviewServiceImpl.java:47–49`**:
```java
if (reviewRepository.existsByCustomerIdAndProductIdAndDeletedFalse(customerId, request.productId())) {
    throw new ValidationException("You have already reviewed this product");
}
```

**`Review.java:34–41`** — Index but no unique constraint:
```java
@Index(name = "idx_reviews_customer_product", columnList = "customer_id, product_id")
```

### Fix

**Step 1** — Add a pessimistic locking query to `ReviewRepository.java`:

Add after line 18:
```java
@jakarta.persistence.LockModeType
@org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT r FROM Review r WHERE r.customerId = :customerId AND r.productId = :productId AND r.deleted = false")
Optional<Review> findByCustomerIdAndProductIdForUpdate(@Param("customerId") UUID customerId, @Param("productId") UUID productId);
```

**Step 2** — In `ReviewServiceImpl.java`, replace the `existsBy` check with the locked query.

Replace `ReviewServiceImpl.java:47–49`:
```java
if (reviewRepository.existsByCustomerIdAndProductIdAndDeletedFalse(customerId, request.productId())) {
    throw new ValidationException("You have already reviewed this product");
}
```

With:
```java
Optional<Review> existing = reviewRepository.findByCustomerIdAndProductIdForUpdate(customerId, request.productId());
if (existing.isPresent()) {
    throw new ValidationException("You have already reviewed this product");
}
```

The `PESSIMISTIC_WRITE` lock acquires a `FOR UPDATE` row-level lock (or a predicate lock on the index gap in PostgreSQL). The second concurrent transaction will block until the first commits, at which point it will see the newly inserted row and correctly reject the duplicate.

---

## BUG-REVIEW-002 — Client-Supplied vendorId in Review Creation Not Verified

| Field | Value |
|---|---|
| **Severity** | **Medium** |
| **Category** | Data Integrity & Security |
| **Affected Files** | `service/ReviewServiceImpl.java`, `dto/CreateReviewRequest.java`, `client/OrderPurchaseVerificationClient.java`, `dto/CustomerProductPurchaseCheckResponse.java` |
| **Lines** | `ReviewServiceImpl.java:58–69`, `CreateReviewRequest.java:11–12` |

### Description

When a customer creates a review, the `vendorId` comes directly from the client request body:

```java
Review review = Review.builder()
        .vendorId(request.vendorId())  // <-- Trusted from client
        ...
        .build();
```

The `OrderPurchaseVerificationClient.checkPurchase()` only verifies that the customer purchased the product — it does **not** return or validate the product's actual vendor. The review-service has no `ProductClient` and no other way to verify the vendor.

A malicious user can tamper with the `vendorId` in the request body, causing:

1. **Review misattribution** — the review appears under the wrong vendor's review list.
2. **Wrong vendor can reply** — `createVendorReply()` checks `review.getVendorId().equals(vendorId)`, so the wrong vendor gets reply access.
3. **Correct vendor cannot reply** — the actual product vendor cannot see or respond to the review.
4. **Skewed analytics** — vendor summary metrics (average rating, review count, reply rate) are distorted.

### Current Code

**`CreateReviewRequest.java:11–12`** — vendorId is a required client field:
```java
@NotNull(message = "vendorId is required")
UUID vendorId,
```

**`ReviewServiceImpl.java:58–69`** — vendorId used directly from request:
```java
Review review = Review.builder()
        .customerId(customerId)
        .customerDisplayName(displayName)
        .productId(request.productId())
        .vendorId(request.vendorId())
        .orderId(purchaseCheck.orderId())
        ...
```

**`CustomerProductPurchaseCheckResponse.java`** — Only returns purchased flag and orderId:
```java
public record CustomerProductPurchaseCheckResponse(
        boolean purchased,
        UUID orderId
) {}
```

### Fix

Enrich the purchase verification response to include the product's vendorId from a trusted source (the order-service, which already knows the vendor for each order item).

**Step 1** — Add `vendorId` to the purchase check response.

Replace `CustomerProductPurchaseCheckResponse.java`:
```java
public record CustomerProductPurchaseCheckResponse(
        boolean purchased,
        UUID orderId,
        UUID vendorId
) {}
```

**Step 2** — In `ReviewServiceImpl.java`, use the verified vendorId from the purchase check instead of the client-supplied value.

Replace `ReviewServiceImpl.java:58–69`:
```java
Review review = Review.builder()
        .customerId(customerId)
        .customerDisplayName(displayName)
        .productId(request.productId())
        .vendorId(purchaseCheck.vendorId())
        .orderId(purchaseCheck.orderId())
        .rating(request.rating())
        .title(request.title())
        .comment(request.comment())
        .images(request.images() != null ? new ArrayList<>(request.images()) : new ArrayList<>())
        .verifiedPurchase(true)
        .build();
```

**Step 3** — Add a null check for safety after the purchase check.

Add after `ReviewServiceImpl.java:56` (after the purchase check):
```java
if (purchaseCheck.vendorId() == null) {
    throw new ValidationException("Unable to determine vendor for this product");
}
```

**Note**: The order-service's `/internal/orders/customers/{customerId}/products/{productId}/purchased` endpoint must also be updated to include the vendorId in its response. The `vendorId` field in `CreateReviewRequest` can remain as an optional hint but should no longer be the source of truth.

---

## BUG-REVIEW-003 — Resilience4j Retries Broken for All 3 Clients

| Field | Value |
|---|---|
| **Severity** | **Medium** |
| **Category** | Architecture & Resilience |
| **Affected Files** | `client/CustomerClient.java`, `client/OrderPurchaseVerificationClient.java`, `client/VendorClient.java` |
| **Lines** | `CustomerClient.java:48–49`, `OrderPurchaseVerificationClient.java:46–47`, `VendorClient.java:53–54` |

### Description

The Resilience4j retry configuration specifies retryable exceptions:

```yaml
retryExceptions:
  - java.io.IOException
  - org.springframework.web.client.ResourceAccessException
  - org.springframework.web.client.HttpServerErrorException
```

However, all three clients catch `RestClientException` (the parent of both `ResourceAccessException` and `HttpServerErrorException`) and **re-wrap** it as `ServiceUnavailableException` **before** the exception reaches the Retry AOP aspect:

```java
// CustomerClient.java:48–49
} catch (RestClientException ex) {
    throw new ServiceUnavailableException("Customer service unavailable", ex);
}
```

```java
// OrderPurchaseVerificationClient.java:46–47
} catch (RestClientException ex) {
    throw new ServiceUnavailableException("Order service unavailable while verifying purchase", ex);
}
```

```java
// VendorClient.java:53–54
} catch (RestClientException ex) {
    throw new ServiceUnavailableException("Vendor service unavailable", ex);
}
```

Since `ServiceUnavailableException` is **not** in the `retryExceptions` list, the Retry aspect sees it and does **not** retry. Transient connectivity failures and 5xx responses immediately surface as errors with zero retries.

### Fix

Add `ServiceUnavailableException` to the retry configuration for all three instances.

**`application.yaml`** — Replace the retry configuration for `orderService` (lines 87–95):
```yaml
      orderService:
        maxAttempts: 3
        waitDuration: 300ms
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
        retryExceptions:
          - java.io.IOException
          - org.springframework.web.client.ResourceAccessException
          - org.springframework.web.client.HttpServerErrorException
          - com.rumal.review_service.exception.ServiceUnavailableException
```

Apply the same change to `customerService` (lines 96–104) and `vendorService` (lines 105–113).

---

## BUG-REVIEW-004 — N+1 Queries on VendorReply for Review List Endpoints

| Field | Value |
|---|---|
| **Severity** | **Low** |
| **Category** | Architecture & Performance |
| **Affected Files** | `entity/Review.java`, `service/ReviewServiceImpl.java` |
| **Lines** | `Review.java:116–117`, `ReviewServiceImpl.java:131–142, 150–156, 160–167, 171–192` |

### Description

The `Review` entity has an inverse `@OneToOne` mapping to `VendorReply`:

```java
@OneToOne(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
private VendorReply vendorReply;
```

In Hibernate, the **inverse side** of a `@OneToOne` (the side with `mappedBy`) **cannot be truly lazy** without bytecode enhancement. Hibernate must query the database to determine whether the association is `null` or points to an existing `VendorReply`. Despite `fetch = FetchType.LAZY`, Hibernate issues an individual `SELECT` for each `Review` when accessing `getVendorReply()`.

The `toResponse()` method accesses this field for every review:

```java
private ReviewResponse toResponse(Review review) {
    VendorReply reply = review.getVendorReply(); // Triggers lazy load
    ...
}
```

For paginated review listings (default page size 10–20), this produces **N+1 queries**: 1 query for the review page + N individual queries for each review's vendor reply. On high-traffic public endpoints like `listByProduct`, this degrades response times significantly.

### Fix

Add a custom repository method that join-fetches `VendorReply` for use in list queries.

**Step 1** — Add a `Specification`-compatible fetch strategy. Since `JpaSpecificationExecutor.findAll(Specification, Pageable)` doesn't support fetch joins directly, use an `@EntityGraph` on a custom query method.

Add to `ReviewRepository.java`:
```java
@EntityGraph(attributePaths = {"vendorReply"})
@Query("SELECT r FROM Review r")
Page<Review> findAllWithVendorReply(Specification<Review> spec, Pageable pageable);
```

**Note**: Spring Data JPA does not support combining `@EntityGraph` with `Specification` on derived query methods out of the box. The practical alternative is to build the join fetch into the Specification itself.

**Step 2** — Create a helper method in `ReviewServiceImpl.java` to add the join fetch to specifications.

Add after `resolveSort()` (~line 424):
```java
private Page<ReviewResponse> findWithVendorReply(Specification<Review> spec, Pageable pageable) {
    Specification<Review> withFetch = (root, query, cb) -> {
        if (query.getResultType() != Long.class && query.getResultType() != long.class) {
            root.fetch("vendorReply", jakarta.persistence.criteria.JoinType.LEFT);
        }
        return spec.toPredicate(root, query, cb);
    };
    return reviewRepository.findAll(withFetch, pageable).map(this::toResponse);
}
```

**Step 3** — Update the four list methods to use the new helper.

Replace `ReviewServiceImpl.java:141`:
```java
return findWithVendorReply(spec, sortedPageable);
```

Replace `ReviewServiceImpl.java:155`:
```java
return findWithVendorReply(spec, pageable);
```

Replace `ReviewServiceImpl.java:166`:
```java
return findWithVendorReply(spec, pageable);
```

Replace `ReviewServiceImpl.java:191`:
```java
return findWithVendorReply(spec, pageable);
```

This changes N+1 queries to a single query with a `LEFT JOIN FETCH`, eliminating the per-review VendorReply lookups.

---

## Summary

| ID | Severity | Category | Title |
|---|---|---|---|
| BUG-REVIEW-001 | **HIGH** | Data Integrity & Concurrency | Duplicate review race condition — no unique constraint on customer+product |
| BUG-REVIEW-002 | Medium | Data Integrity & Security | Client-supplied vendorId in review creation not verified against product |
| BUG-REVIEW-003 | Medium | Architecture & Resilience | Resilience4j retries broken for all 3 clients |
| BUG-REVIEW-004 | Low | Architecture & Performance | N+1 queries on VendorReply for review list endpoints |
