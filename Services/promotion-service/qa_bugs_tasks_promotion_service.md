# QA Bugs & Tasks — promotion-service

> **Auditor**: Principal Backend QA Architect & Senior Security Bug Hunter
> **Service**: `promotion-service` (port 8091)
> **Date**: 2026-02-27
> **Findings**: 4 total — 1 Critical, 1 High, 1 Medium, 1 Low

---

## Domain Map

| Layer | Component | Key Responsibility |
|---|---|---|
| Controller | `AdminPromotionController` (`/admin/promotions`) | 9 endpoints: CRUD + submit/approve/reject/activate/pause/archive |
| Controller | `AdminPromotionCouponController` (`/admin/promotions/{id}/coupons`) | 5 endpoints: coupon code CRUD, batch create, activate/deactivate |
| Controller | `AdminPromotionAnalyticsController` (`/admin/promotions/analytics`) | 2 endpoints: analytics list + per-promotion analytics |
| Controller | `CustomerPromotionController` (`/promotions/me`) | 1 endpoint: customer coupon usage history |
| Controller | `PublicPromotionController` (`/promotions`) | 3 endpoints: public listing, flash sales, get by ID (no auth) |
| Controller | `InternalPromotionQuoteController` (`/internal/promotions/quote`) | 1 endpoint: promotion quote engine |
| Controller | `InternalPromotionReservationController` (`/internal/promotions/reservations`) | 3 endpoints: reserve/commit/release coupon reservation |
| Controller | `InternalPromotionAnalyticsController` (`/internal/promotions/analytics`) | 3 endpoints: platform/vendor analytics summaries |
| Service | `PromotionCampaignService` | Core campaign CRUD + lifecycle state machine + approval workflow |
| Service | `PromotionQuoteService` | Quote engine: loads all active promotions, applies stacking/exclusivity/budget/cap rules |
| Service | `CouponReservationService` | Reserve (SERIALIZABLE), commit, release coupons with budget tracking |
| Service | `CouponValidationService` | Coupon eligibility: active, time windows, usage limits (global + per-customer) |
| Service | `CouponCodeAdminService` | Coupon code CRUD, batch generation with SecureRandom |
| Service | `AdminPromotionAccessScopeService` | RBAC: super_admin, platform_staff, vendor_admin, vendor_staff with per-promotion tenant isolation |
| Service | `PublicPromotionService` | Public listing: only ACTIVE + APPROVED/NOT_REQUIRED within time windows |
| Service | `PromotionAnalyticsService` | Per-promotion analytics: coupon/reservation counts, budget utilization |
| Service | `InternalPromotionAnalyticsService` | Platform/vendor summary analytics |
| Service | `CustomerPromotionService` | Customer committed coupon usage history |
| Service | `CouponReservationCleanupJob` | Scheduled job: expire stale RESERVED reservations |
| Repository | `PromotionCampaignRepository` | JpaSpecificationExecutor, pessimistic `findByIdForUpdate`, atomic `incrementFlashSaleRedemptionCount` |
| Repository | `CouponCodeRepository` | `findByCodeWithPromotion` (join fetch), pessimistic `findByCodeWithPromotionForUpdate` |
| Repository | `CouponReservationRepository` | Pessimistic `findByIdForUpdate` (join fetch chain), idempotent `findByRequestKey`, `expireStaleReservations` |
| Entity | `PromotionCampaign` | `@Version`, budget tracking (`budgetAmount`/`burnedBudgetAmount`), flash sale support, stacking rules |
| Entity | `CouponCode` | `@Version`, `@ManyToOne` PromotionCampaign, unique code, maxUses/maxUsesPerCustomer |
| Entity | `CouponReservation` | `@Version`, `@ManyToOne` CouponCode, requestKey (idempotency), status state machine |
| Entity | `PromotionSpendTier` | `@Embeddable`, thresholdAmount/discountAmount for tiered spend promotions |
| Client | `AccessClient` | Fetch platform/vendor staff access (CB: accessService) |
| Client | `VendorAccessClient` | Fetch vendor memberships (CB: vendorService) |
| Client | `CustomerClient` | Fetch customer by keycloakId (CB: customerService) |
| Filter | `PromotionMutationIdempotencyFilter` | Redis-backed idempotency for admin + internal POST/PUT/PATCH mutations |
| Scheduler | `CouponReservationCleanupJob` | Expire stale reservations every 60s |

---

## BUG-PROMO-001 — Line-Item Promotion Cap (`maximumDiscountAmount`) Not Applied to Line State Discounts

| Field | Value |
|---|---|
| **Severity** | **CRITICAL** |
| **Category** | Logic & Runtime (Financial Accuracy) |
| **Affected Files** | `service/PromotionQuoteService.java` |
| **Lines** | 293–310, 342–369 |

### Description

When a LINE_ITEM promotion has a `maximumDiscountAmount` cap, the cap is applied to the **returned** `totalDiscount` value, but the individual **line state** discounts (which are mutated via `line.applyLineDiscount()`) retain their **uncapped** values. Since the final `grandTotal` calculation at lines 218–230 sums discounts from line states (not from the applied promotions list), the cap is silently ignored in the final pricing.

**Impact**: The customer receives more discount than the `maximumDiscountAmount` allows. For a platform handling millions of transactions, even small per-order discrepancies compound into massive revenue loss. A platform admin sets `maximumDiscountAmount = $15` specifically to limit financial exposure, but this limit is silently bypassed.

**Example trace**:
- 20% OFF LINE_ITEM promotion, `maximumDiscountAmount = $15`
- Cart: Line A ($50), Line B ($50). Subtotal = $100
- Line A discount: $10, Line B discount: $10 → uncapped total = $20
- Cap applied: `totalDiscount = min($20, $15) = $15` (returned to caller)
- **But** line states: A.lineDiscount = $10, B.lineDiscount = $10 (uncapped, still $20)
- `lineDiscountTotal` (line 218) = $10 + $10 = **$20** (reads from line states)
- `grandTotal` = $100 − $20 = **$80** (should be $100 − $15 = **$85**)
- Applied promotions list reports $15, but actual discount applied is $20 — **$5 over-discount**

The same bug exists in `applyBuyXGetYLinePromotion` (lines 342–369) which also applies the cap after mutating line states.

### Current Code

**`PromotionQuoteService.java:293–310`** — Regular LINE_ITEM discount application:
```java
BigDecimal totalDiscount = BigDecimal.ZERO;
for (LineState line : eligibleLines) {
    BigDecimal remaining = line.lineTotal();
    if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
        continue;
    }
    BigDecimal discount = calculateDiscountForAmount(promotion, remaining);
    if (discount.compareTo(BigDecimal.ZERO) <= 0) {
        continue;
    }
    line.applyLineDiscount(discount);            // ← mutates line state with UNCAPPED discount
    totalDiscount = totalDiscount.add(discount);
}
totalDiscount = applyPromotionCap(promotion, normalizeMoney(totalDiscount));  // ← caps return value only
if (totalDiscount.compareTo(BigDecimal.ZERO) <= 0) {
    return PromotionApplicationResult.rejected("Promotion produced no discount");
}
return PromotionApplicationResult.applied(totalDiscount);  // ← capped value, but line states are uncapped
```

**`PromotionQuoteService.java:218–227`** — Final grand total uses uncapped line states:
```java
BigDecimal lineDiscountTotal = normalizeMoney(lineStates.stream()
        .map(LineState::lineDiscount)       // ← reads UNCAPPED values from line states
        .reduce(BigDecimal.ZERO, BigDecimal::add));

shippingDiscountTotal = minMoney(shippingDiscountTotal, shippingAmount);
cartDiscountTotal = minMoney(cartDiscountTotal, normalizeMoney(subtotal.subtract(lineDiscountTotal)));
BigDecimal totalDiscount = normalizeMoney(lineDiscountTotal.add(cartDiscountTotal).add(shippingDiscountTotal));
BigDecimal grandTotal = normalizeMoney(
        subtotal.subtract(lineDiscountTotal).subtract(cartDiscountTotal)  // ← uses uncapped lineDiscountTotal
                .add(shippingAmount).subtract(shippingDiscountTotal)
);
```

### Fix

**Step 1** — In `applyLineLevelPromotion`, snapshot line discounts before applying, then proportionally scale down if the cap reduces the total.

Replace `PromotionQuoteService.java:293–310`:
```java
List<BigDecimal> beforeDiscounts = eligibleLines.stream()
        .map(l -> normalizeMoney(l.lineDiscount()))
        .toList();

BigDecimal totalDiscount = BigDecimal.ZERO;
List<BigDecimal> appliedPerLine = new ArrayList<>(eligibleLines.size());
for (LineState line : eligibleLines) {
    BigDecimal remaining = line.lineTotal();
    if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
        appliedPerLine.add(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        continue;
    }
    BigDecimal discount = calculateDiscountForAmount(promotion, remaining);
    if (discount.compareTo(BigDecimal.ZERO) <= 0) {
        appliedPerLine.add(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        continue;
    }
    BigDecimal applied = line.applyLineDiscount(discount);
    appliedPerLine.add(applied);
    totalDiscount = totalDiscount.add(applied);
}

BigDecimal uncapped = normalizeMoney(totalDiscount);
totalDiscount = applyPromotionCap(promotion, uncapped);

if (totalDiscount.compareTo(uncapped) < 0 && uncapped.compareTo(BigDecimal.ZERO) > 0) {
    // Restore line states to pre-application snapshot
    for (int i = 0; i < eligibleLines.size(); i++) {
        eligibleLines.get(i).lineDiscount = beforeDiscounts.get(i);
    }
    // Re-apply with proportionally scaled-down discounts
    BigDecimal scaleFactor = totalDiscount.divide(uncapped, 10, RoundingMode.HALF_UP);
    BigDecimal reappliedTotal = BigDecimal.ZERO;
    for (int i = 0; i < eligibleLines.size(); i++) {
        BigDecimal scaled = normalizeMoney(appliedPerLine.get(i).multiply(scaleFactor));
        BigDecimal reapplied = eligibleLines.get(i).applyLineDiscount(scaled);
        reappliedTotal = reappliedTotal.add(reapplied);
    }
    totalDiscount = normalizeMoney(reappliedTotal);
}

if (totalDiscount.compareTo(BigDecimal.ZERO) <= 0) {
    return PromotionApplicationResult.rejected("Promotion produced no discount");
}
return PromotionApplicationResult.applied(totalDiscount);
```

**Step 2** — Apply the same fix to `applyBuyXGetYLinePromotion`.

Replace `PromotionQuoteService.java:342–369`:
```java
List<BigDecimal> beforeDiscounts = cheapestFirst.stream()
        .map(l -> normalizeMoney(l.lineDiscount()))
        .toList();

BigDecimal totalDiscount = BigDecimal.ZERO;
List<BigDecimal> appliedPerLine = new ArrayList<>(cheapestFirst.size());
for (LineState line : cheapestFirst) {
    if (freeUnitsRemaining <= 0) {
        appliedPerLine.add(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        continue;
    }
    if (line.request().quantity() <= 0 || line.lineTotal().compareTo(BigDecimal.ZERO) <= 0) {
        appliedPerLine.add(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        continue;
    }
    int freeUnitsForLine = Math.min(freeUnitsRemaining, line.request().quantity());
    if (freeUnitsForLine <= 0) {
        appliedPerLine.add(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        continue;
    }
    BigDecimal discount = bogoDiscountForUnits(line, freeUnitsForLine);
    if (discount.compareTo(BigDecimal.ZERO) <= 0) {
        appliedPerLine.add(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        continue;
    }
    BigDecimal applied = line.applyLineDiscount(discount);
    appliedPerLine.add(applied);
    if (applied.compareTo(BigDecimal.ZERO) > 0) {
        totalDiscount = totalDiscount.add(applied);
        freeUnitsRemaining -= freeUnitsForLine;
    }
}

BigDecimal uncapped = normalizeMoney(totalDiscount);
totalDiscount = applyPromotionCap(promotion, uncapped);

if (totalDiscount.compareTo(uncapped) < 0 && uncapped.compareTo(BigDecimal.ZERO) > 0) {
    for (int i = 0; i < cheapestFirst.size(); i++) {
        cheapestFirst.get(i).lineDiscount = beforeDiscounts.get(i);
    }
    BigDecimal scaleFactor = totalDiscount.divide(uncapped, 10, RoundingMode.HALF_UP);
    BigDecimal reappliedTotal = BigDecimal.ZERO;
    for (int i = 0; i < cheapestFirst.size(); i++) {
        BigDecimal scaled = normalizeMoney(appliedPerLine.get(i).multiply(scaleFactor));
        BigDecimal reapplied = cheapestFirst.get(i).applyLineDiscount(scaled);
        reappliedTotal = reappliedTotal.add(reapplied);
    }
    totalDiscount = normalizeMoney(reappliedTotal);
}

if (totalDiscount.compareTo(BigDecimal.ZERO) <= 0) {
    return PromotionApplicationResult.rejected("BUY_X_GET_Y produced no discount");
}
return PromotionApplicationResult.applied(totalDiscount);
```

---

## BUG-PROMO-002 — Flash Sale Redemption Count Never Incremented

| Field | Value |
|---|---|
| **Severity** | **HIGH** |
| **Category** | Logic & Runtime |
| **Affected Files** | `service/CouponReservationService.java`, `repo/PromotionCampaignRepository.java` |
| **Lines** | `CouponReservationService.java:109–133`, `PromotionCampaignRepository.java:34–36` |

### Description

The `PromotionCampaignRepository` defines an atomic increment method for flash sale redemption tracking:

```java
@Modifying
@Query("UPDATE PromotionCampaign p SET p.flashSaleRedemptionCount = p.flashSaleRedemptionCount + 1 WHERE p.id = :id AND p.flashSaleRedemptionCount < p.flashSaleMaxRedemptions")
int incrementFlashSaleRedemptionCount(@Param("id") UUID id);
```

However, this method is **never called** from any service. When a coupon reservation is committed (`CouponReservationService.commit()`), the method increments `burnedBudgetAmount` but does **not** increment `flashSaleRedemptionCount`.

Meanwhile, `PromotionQuoteService.quote()` checks the count at line 166–169:
```java
if (promotion.isFlashSale() && promotion.getFlashSaleMaxRedemptions() != null
        && promotion.getFlashSaleRedemptionCount() >= promotion.getFlashSaleMaxRedemptions()) {
    rejected.add(...);
    continue;
}
```

Since the count is never incremented from its initial value of 0, flash sale limits configured via `flashSaleMaxRedemptions` are **never enforced**. A flash sale set to 100 maximum redemptions will accept unlimited redemptions.

### Current Code

**`CouponReservationService.java:109–133`** — `commit()` increments budget but not flash sale count:
```java
@Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 20)
public CouponReservationResponse commit(UUID reservationId, CommitCouponReservationRequest request) {
    CouponReservation reservation = couponReservationRepository.findByIdForUpdate(reservationId)
            .orElseThrow(() -> new ResourceNotFoundException("Coupon reservation not found: " + reservationId));

    if (reservation.getStatus() == CouponReservationStatus.COMMITTED) {
        if (reservation.getOrderId() != null && !reservation.getOrderId().equals(request.orderId())) {
            throw new ValidationException("Reservation already committed to a different order");
        }
        return toResponse(reservation, null);
    }
    // ... status checks ...

    incrementPromotionBurnedBudgetIfApplicable(reservation.getPromotionId(), reservation.getReservedDiscountAmount());
    // ← no flash sale redemption count increment
    reservation.setStatus(CouponReservationStatus.COMMITTED);
    reservation.setOrderId(request.orderId());
    reservation.setCommittedAt(Instant.now());
    return toResponse(couponReservationRepository.save(reservation), null);
}
```

### Fix

In `CouponReservationService.commit()`, after incrementing the burned budget, also increment the flash sale redemption count if applicable.

Replace `CouponReservationService.java:128–132`:
```java
    incrementPromotionBurnedBudgetIfApplicable(reservation.getPromotionId(), reservation.getReservedDiscountAmount());
    incrementFlashSaleRedemptionIfApplicable(reservation.getPromotionId());
    reservation.setStatus(CouponReservationStatus.COMMITTED);
    reservation.setOrderId(request.orderId());
    reservation.setCommittedAt(Instant.now());
    return toResponse(couponReservationRepository.save(reservation), null);
```

Add new method to `CouponReservationService`:
```java
private void incrementFlashSaleRedemptionIfApplicable(UUID promotionId) {
    if (promotionId == null) {
        return;
    }
    var promotion = promotionCampaignRepository.findByIdForUpdate(promotionId)
            .orElse(null);
    if (promotion == null || !promotion.isFlashSale() || promotion.getFlashSaleMaxRedemptions() == null) {
        return;
    }
    int updated = promotionCampaignRepository.incrementFlashSaleRedemptionCount(promotionId);
    if (updated == 0) {
        throw new ValidationException("Flash sale redemption limit reached");
    }
}
```

**Note**: `incrementFlashSaleRedemptionCount` is an atomic CAS-like query (`WHERE flashSaleRedemptionCount < flashSaleMaxRedemptions`) — if 0 rows are updated, the limit is reached and the commit should fail. This is already safe for concurrent access.

---

## BUG-PROMO-003 — Resilience4j Retries Broken for AccessClient and VendorAccessClient

| Field | Value |
|---|---|
| **Severity** | **Medium** |
| **Category** | Architecture & Resilience |
| **Affected Files** | `client/AccessClient.java`, `client/VendorAccessClient.java`, `application.yaml` |
| **Lines** | `AccessClient.java:41–45,59–63`, `VendorAccessClient.java:40–44`, `application.yaml:83–100` |

### Description

The Resilience4j retry configuration specifies retryable exceptions:

```yaml
retryExceptions:
  - java.io.IOException
  - org.springframework.web.client.ResourceAccessException
  - org.springframework.web.client.HttpServerErrorException
```

However, both `AccessClient` and `VendorAccessClient` catch `RestClientException` (the parent of both `ResourceAccessException` and `HttpServerErrorException`) and **re-wrap** it as `ServiceUnavailableException` **before** the exception reaches the Retry AOP aspect:

```java
// AccessClient.java:41–45
} catch (RestClientResponseException ex) {
    throw new ServiceUnavailableException("Access service platform lookup failed (" + ex.getStatusCode().value() + ")", ex);
} catch (RestClientException | IllegalStateException ex) {
    throw new ServiceUnavailableException("Access service unavailable for platform lookup", ex);
}
```

Since `ServiceUnavailableException` is **not** in the `retryExceptions` list, the Retry aspect sees it and does **not** retry. Transient connectivity failures and 5xx responses immediately surface as errors with zero retries.

**Note**: `CustomerClient` is **not** affected — it only catches `HttpClientErrorException` for 404 (re-throwing `ResourceNotFoundException`), letting other exceptions like `ResourceAccessException` and `HttpServerErrorException` propagate correctly to the Retry aspect.

### Fix

Add `ServiceUnavailableException` to the retry configuration for the affected instances.

**`application.yaml`** — Replace the retry config for `accessService` (lines 92–100):
```yaml
      accessService:
        maxAttempts: 3
        waitDuration: 300ms
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
        retryExceptions:
          - java.io.IOException
          - org.springframework.web.client.ResourceAccessException
          - org.springframework.web.client.HttpServerErrorException
          - com.rumal.promotion_service.exception.ServiceUnavailableException
```

Apply the same change to `vendorService` (lines 83–91).

---

## BUG-PROMO-004 — Missing OptimisticLockingFailureException Handler Returns 500 Instead of 409

| Field | Value |
|---|---|
| **Severity** | **Low** |
| **Category** | Logic & Runtime |
| **Affected Files** | `exception/GlobalExceptionHandler.java` |
| **Lines** | 63–72 |

### Description

All three core entities (`PromotionCampaign`, `CouponCode`, `CouponReservation`) use `@Version` for optimistic locking. When a concurrent update triggers `OptimisticLockingFailureException`, it falls through to the catch-all `Exception` handler which returns **500 "Unexpected error"** instead of **409 Conflict**.

Other services in the platform (e.g., product-service) correctly return 409 for this case. The inconsistency causes:

1. Monitoring systems to flag these as server errors instead of expected concurrency conflicts.
2. Clients unable to distinguish "retry due to conflict" from "server is broken."

### Current Code

**`GlobalExceptionHandler.java:63–72`** — Catch-all returns 500:
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

### Fix

Add a dedicated handler for `OptimisticLockingFailureException` **before** the catch-all.

**`GlobalExceptionHandler.java`** — Add before the catch-all handler:
```java
@ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
public ResponseEntity<?> optimisticLock(org.springframework.dao.OptimisticLockingFailureException ex) {
    log.warn("Promotion optimistic locking conflict: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(error(HttpStatus.CONFLICT, "Concurrent modification conflict. Please retry."));
}
```

---

## Summary

| ID | Severity | Category | Title |
|---|---|---|---|
| BUG-PROMO-001 | **CRITICAL** | Logic & Runtime | Line-item promotion cap (`maximumDiscountAmount`) not applied to line state discounts — grandTotal over-discounted |
| BUG-PROMO-002 | **HIGH** | Logic & Runtime | Flash sale redemption count never incremented — `flashSaleMaxRedemptions` limits unenforceable |
| BUG-PROMO-003 | Medium | Architecture & Resilience | Resilience4j retries broken for AccessClient and VendorAccessClient |
| BUG-PROMO-004 | Low | Logic & Runtime | Missing OptimisticLockingFailureException handler returns 500 instead of 409 |
