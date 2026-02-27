# QA Bugs & Tasks — cart-service

> **Auditor**: Principal Backend QA Architect & Senior Security Bug Hunter
> **Service**: `cart-service` (port 8085)
> **Date**: 2026-02-27
> **Findings**: 4 total — 0 Critical, 1 High, 2 Medium, 1 Low

---

## Domain Map

| Layer | Component | Key Responsibility |
|---|---|---|
| Controller | `CartController` (`/cart`) | 10 customer-facing endpoints (CRUD, checkout, save-for-later) |
| Controller | `InternalCartAnalyticsController` (`/internal/cart/analytics`) | Platform-level analytics for analytics-service |
| Service | `CartService` | Core cart logic — add/update/remove items, checkout orchestration, promotion quoting |
| Service | `CartAnalyticsService` | Aggregates analytics from cart/item repos |
| Service | `ShippingFeeCalculator` | Vendor-based + international shipping fee computation |
| Repository | `CartRepository` | Cart CRUD, pessimistic locking, batch expiry |
| Repository | `CartItemRepository` | Analytics aggregate queries |
| Entity | `Cart` | `keycloakId` (unique), `@Version`, `OneToMany` items, `lastActivityAt` |
| Entity | `CartItem` | `productId`, `quantity`, `lineTotal`, `savedForLater` flag |
| Client | `ProductClient` | Fetch product details (CB: productService) |
| Client | `OrderClient` | Create order during checkout (CB: orderService) |
| Client | `PromotionClient` | Quote pricing, reserve/release coupons (CB: promotionService) |
| Client | `CustomerClient` | Fetch customer/address (CB: customerService) |
| Client | `VendorOperationalStateClient` | Check vendor operational state (CB: vendorService) |
| Filter | `CartMutationIdempotencyFilter` | Redis-backed idempotency for POST/PUT/DELETE mutations |
| Scheduler | `CartExpiryScheduler` | Purge carts inactive > 30 days in batches |

---

## BUG-CART-001 — Checkout and Preview Include Saved-For-Later Items

| Field | Value |
|---|---|
| **Severity** | **HIGH** |
| **Category** | Logic & Runtime |
| **Affected Files** | `service/CartService.java` |
| **Lines** | 200, 225–233, 318, 352, 368–374 |

### Description

The `checkout()` and `previewCheckout()` methods operate on **all** `cart.getItems()` without filtering out items where `savedForLater = true`. This causes:

1. **Saved-for-later items are included in the order** — the customer is charged for items they explicitly set aside.
2. **Saved-for-later items are deleted after checkout** — `cart.getItems().clear()` at line 318 removes everything.
3. **Checkout preview pricing includes saved-for-later items** — the quoted total is wrong.

The `checkoutSnapshot()` method (line 532–540) also includes saved-for-later items in the snapshot used for change detection.

### Current Code

**`CartService.java:200`** — Snapshot includes all items:
```java
List<CartCheckoutLine> expectedSnapshot = checkoutSnapshot(previewCart);
```

**`CartService.java:225–233`** — Order items built from all items (no filter):
```java
for (CartItem cartItem : cart.getItems()) {
    ProductDetails latest = latestProductsById.get(cartItem.getProductId());
    if (latest == null) {
        throw new ValidationException("Product is not available: " + cartItem.getProductId());
    }
    orderItems.add(new CreateMyOrderItemRequest(cartItem.getProductId(), cartItem.getQuantity()));
    totalQuantity += cartItem.getQuantity();
    subtotal = subtotal.add(calculateLineTotal(normalizeMoney(latest.sellingPrice()), cartItem.getQuantity()));
}
```

**`CartService.java:317–319`** — Cart clear deletes saved-for-later items:
```java
if (checkoutSnapshot(cart).equals(expectedSnapshot)) {
    cart.getItems().clear();
    cartRepository.save(cart);
```

**`CartService.java:532–540`** — Snapshot includes all items:
```java
private List<CartCheckoutLine> checkoutSnapshot(Cart cart) {
    if (cart == null || cart.getItems() == null) {
        return List.of();
    }
    return cart.getItems().stream()
            .map(item -> new CartCheckoutLine(item.getProductId(), item.getQuantity()))
            .sorted(Comparator.comparing(line -> line.productId() == null ? "" : line.productId().toString()))
            .toList();
}
```

### Fix

**Step 1** — Add a helper to get only active (non-saved-for-later) items:

**`CartService.java`** — Add after `checkoutSnapshot` method (~line 541):
```java
private List<CartItem> activeCartItems(Cart cart) {
    if (cart == null || cart.getItems() == null) {
        return List.of();
    }
    return cart.getItems().stream()
            .filter(item -> !item.isSavedForLater())
            .toList();
}
```

**Step 2** — Filter `checkoutSnapshot` to active items only:

Replace `CartService.java:532–540`:
```java
private List<CartCheckoutLine> checkoutSnapshot(Cart cart) {
    return activeCartItems(cart).stream()
            .map(item -> new CartCheckoutLine(item.getProductId(), item.getQuantity()))
            .sorted(Comparator.comparing(line -> line.productId() == null ? "" : line.productId().toString()))
            .toList();
}
```

**Step 3** — In `checkout()`, build order items from active items only:

Replace `CartService.java:225–233`:
```java
for (CartItem cartItem : activeCartItems(cart)) {
    ProductDetails latest = latestProductsById.get(cartItem.getProductId());
    if (latest == null) {
        throw new ValidationException("Product is not available: " + cartItem.getProductId());
    }
    orderItems.add(new CreateMyOrderItemRequest(cartItem.getProductId(), cartItem.getQuantity()));
    totalQuantity += cartItem.getQuantity();
    subtotal = subtotal.add(calculateLineTotal(normalizeMoney(latest.sellingPrice()), cartItem.getQuantity()));
}
```

**Step 4** — In `checkout()`, only remove active items after checkout (preserve saved-for-later):

Replace `CartService.java:317–319`:
```java
if (checkoutSnapshot(cart).equals(expectedSnapshot)) {
    cart.getItems().removeIf(item -> !item.isSavedForLater());
    cartRepository.save(cart);
```

**Step 5** — In `checkout()`, check empty using active items only:

Replace `CartService.java:196–198`:
```java
List<CartItem> activeItems = activeCartItems(previewCart);
if (activeItems.isEmpty()) {
    throw new ValidationException("Cart is empty");
}
```

And replace `CartService.java:214–215`:
```java
if (activeCartItems(cart).isEmpty()) {
    throw new ValidationException("Cart is empty");
}
```

**Step 6** — In `previewCheckout()`, use active items only:

Replace `CartService.java:348–350`:
```java
List<CartItem> activeItems = activeCartItems(cart);
if (activeItems.isEmpty()) {
    throw new ValidationException("Cart is empty");
}
```

And replace `CartService.java:368–374`:
```java
for (CartItem item : activeItems) {
    ProductDetails latest = latestProductsById.get(item.getProductId());
    if (latest == null) {
        throw new ValidationException("Product is not available: " + item.getProductId());
    }
    totalQuantity += item.getQuantity();
}
```

**Step 7** — In `buildPromotionQuoteRequest()`, use active items only:

Replace `CartService.java:630–631`:
```java
if (cart == null || cart.getItems() == null || activeCartItems(cart).isEmpty()) {
    throw new ValidationException("Cart is empty");
}
```

Replace `CartService.java:633–634`:
```java
List<CartItem> activeItems = activeCartItems(cart);
List<PromotionQuoteLineRequest> quoteLines = new java.util.ArrayList<>(activeItems.size());
for (CartItem item : activeItems) {
```

**Step 8** — In `calculateShippingForCart()`, use active items only:

Replace `CartService.java:665–666`:
```java
if (cart == null || cart.getItems() == null || activeCartItems(cart).isEmpty()) {
    return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
}
```

Replace `CartService.java:668–669`:
```java
List<CartItem> activeItems = activeCartItems(cart);
List<ShippingFeeCalculator.ShippingLine> shippingLines = new java.util.ArrayList<>(activeItems.size());
for (CartItem item : activeItems) {
```

---

## BUG-CART-002 — Cart Expiry Scheduler Cannot Delete Carts With Items

| Field | Value |
|---|---|
| **Severity** | **Medium** |
| **Category** | Data Integrity & Concurrency |
| **Affected Files** | `scheduler/CartExpiryScheduler.java`, `repo/CartRepository.java`, `entity/CartItem.java` |
| **Lines** | `CartExpiryScheduler.java:36–41`, `CartRepository.java:29–30` |

### Description

The `CartExpiryScheduler` uses the native SQL query `deleteExpiredCartsBatch` to purge old carts:

```sql
DELETE FROM carts WHERE id IN (SELECT id FROM carts WHERE last_activity_at < :cutoff LIMIT :batchSize)
```

This native SQL bypasses JPA's `orphanRemoval = true` on `Cart.items`. Since Hibernate does **not** generate `ON DELETE CASCADE` on the `cart_items.cart_id` foreign key by default, any attempt to delete a cart that has items fails with a **foreign key constraint violation**.

The scheduler's `catch (Exception ex)` silently logs the error, so the failure is hidden. Expired carts that contain items (the common case — users abandon carts with items) are **never purged**, causing unbounded storage growth.

### Current Code

**`CartExpiryScheduler.java:36–41`**:
```java
Integer result = transactionTemplate.execute(status ->
        cartRepository.deleteExpiredCartsBatch(cutoff, batchSize));
deleted = result != null ? result : 0;
```

**`CartRepository.java:29–30`**:
```java
@Modifying
@Query(value = "DELETE FROM carts WHERE id IN (SELECT id FROM carts WHERE last_activity_at < :cutoff LIMIT :batchSize)", nativeQuery = true)
int deleteExpiredCartsBatch(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);
```

### Fix — Add `@OnDelete(CASCADE)` to the CartItem FK

This makes the database handle cascading deletes, which is required for both native SQL and JPQL bulk deletes.

**`entity/CartItem.java:39–41`** — Replace:
```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "cart_id", nullable = false)
private Cart cart;
```

With:
```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "cart_id", nullable = false)
@org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
private Cart cart;
```

This instructs Hibernate to add `ON DELETE CASCADE` to the generated FK constraint, allowing the native SQL `DELETE FROM carts` to automatically cascade to `cart_items`.

---

## BUG-CART-003 — Resilience4j Retries Broken for ProductClient, OrderClient, VendorOperationalStateClient

| Field | Value |
|---|---|
| **Severity** | **Medium** |
| **Category** | Architecture & Resilience |
| **Affected Files** | `client/ProductClient.java`, `client/OrderClient.java`, `client/VendorOperationalStateClient.java` |
| **Lines** | `ProductClient.java:43–45`, `OrderClient.java:85–86`, `VendorOperationalStateClient.java:49–50` |

### Description

The Resilience4j retry configuration specifies retryable exceptions:

```yaml
retryExceptions:
  - java.io.IOException
  - org.springframework.web.client.ResourceAccessException
  - org.springframework.web.client.HttpServerErrorException
```

However, three clients catch `RestClientException` (the parent of both `ResourceAccessException` and `HttpServerErrorException`) and **re-wrap** it as `ServiceUnavailableException` **before** the exception reaches the Retry AOP aspect:

```java
// ProductClient.java:43–45
} catch (RestClientException ex) {
    throw new ServiceUnavailableException("Service unavailable: " + ex.getMessage());
}
```

Since `ServiceUnavailableException` is **not** in the `retryExceptions` list, the Retry aspect sees it and does **not** retry. All three clients have identical patterns. Transient connectivity failures and 5xx responses immediately surface as errors with zero retries.

**Note**: `PromotionClient` and `CustomerClient` do **not** catch `RestClientException` broadly, so their retries work correctly.

### Fix

Add `ServiceUnavailableException` to the retry configuration for the affected circuit breaker instances.

**`application.yaml`** — For each affected instance (`productService`, `orderService`, `vendorService`), add `ServiceUnavailableException` to `retryExceptions`:

Replace the retry configuration for `productService` (lines 110–118):
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
          - com.rumal.cart_service.exception.ServiceUnavailableException
```

Apply the same change to `orderService` (lines 119–127) and `vendorService` (lines 128–136).

**Important**: For `orderService`, retries on the `createMyOrder` call are safe because the cart-service already passes a downstream idempotency key (`"cart-checkout_" + incomingKey`), and the order-service enforces idempotency. However, if the retry concern is too high, you can alternatively fix only `productService` and `vendorService`, while leaving `orderService` without retry (the current effective behavior).

---

## BUG-CART-004 — removeItem and updateItem Do Not Update lastActivityAt

| Field | Value |
|---|---|
| **Severity** | **Low** |
| **Category** | Logic & Runtime |
| **Affected Files** | `service/CartService.java` |
| **Lines** | 161–172, 133–155 |

### Description

The cart expiry scheduler purges carts where `lastActivityAt` is older than 30 days. Most mutation methods update `lastActivityAt` to `Instant.now()`:

| Method | Updates `lastActivityAt`? |
|---|---|
| `addItem()` | Yes (line 119) |
| `updateItem()` | **No** |
| `removeItem()` | **No** |
| `clear()` | No (cart is empty after, moot) |
| `saveForLater()` | Yes (line 729) |
| `moveToCart()` | Yes (line 746) |
| `updateNote()` | Yes (line 768) |

A customer who only updates quantities or removes items (without adding new items) does not reset the expiry timer. Their cart could be purged even though they are actively managing it.

### Fix

**`CartService.java:133–155`** — In `updateItem`, add `lastActivityAt` update after line 150:

Replace:
```java
            item.setQuantity(quantity);
            refreshCartItemSnapshot(item, product);
            item.setLineTotal(calculateLineTotal(item.getUnitPrice(), item.getQuantity()));

            Cart saved = cartRepository.save(cart);
```

With:
```java
            item.setQuantity(quantity);
            refreshCartItemSnapshot(item, product);
            item.setLineTotal(calculateLineTotal(item.getUnitPrice(), item.getQuantity()));
            cart.setLastActivityAt(Instant.now());

            Cart saved = cartRepository.save(cart);
```

**`CartService.java:161–172`** — In `removeItem`, add `lastActivityAt` update after line 170:

Replace:
```java
        boolean removed = cart.getItems().removeIf(item -> item.getId().equals(itemId));
        if (!removed) {
            throw new ResourceNotFoundException("Cart item not found: " + itemId);
        }
        cartRepository.save(cart);
```

With:
```java
        boolean removed = cart.getItems().removeIf(item -> item.getId().equals(itemId));
        if (!removed) {
            throw new ResourceNotFoundException("Cart item not found: " + itemId);
        }
        cart.setLastActivityAt(Instant.now());
        cartRepository.save(cart);
```

---

## Summary

| ID | Severity | Category | Title |
|---|---|---|---|
| BUG-CART-001 | **HIGH** | Logic & Runtime | Checkout and preview include saved-for-later items |
| BUG-CART-002 | Medium | Data Integrity | Cart expiry scheduler fails for carts with items (FK violation) |
| BUG-CART-003 | Medium | Architecture & Resilience | Resilience4j retries broken for 3 of 5 clients |
| BUG-CART-004 | Low | Logic & Runtime | removeItem and updateItem don't update lastActivityAt |
