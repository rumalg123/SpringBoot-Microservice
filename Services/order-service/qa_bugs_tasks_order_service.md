# QA Bugs & Tasks — order-service

> **Auditor**: Principal Backend QA Architect & Senior Security Bug Hunter
> **Service**: `order-service` (port 8082)
> **Date**: 2026-02-27
> **Findings**: 4 total — 0 Critical, 1 High, 2 Medium, 1 Low

---

## Domain Map

| Layer | Component | Key Responsibility |
|---|---|---|
| Controller | `OrderController` (`/orders`) | Admin CRUD, customer self-service (`/me`), CSV export, invoice, status management |
| Controller | `VendorOrderSelfServiceController` (`/orders/vendor/me`) | Vendor self-service: list, get, set tracking |
| Controller | `InternalOrderController` (`/internal/orders`) | Vendor deletion check, customer purchase check |
| Controller | `InternalOrderAnalyticsController` (`/internal/orders/analytics`) | Platform/vendor/customer analytics |
| Service | `OrderService` | Core: order creation, status machine, vendor order management, cancel, CSV export, invoice, shipping address update |
| Service | `OutboxService` | Transactional outbox: enqueue events for inventory/promotion compensation, dispatch with retries |
| Service | `OrderAnalyticsService` | Analytics aggregation queries |
| Service | `OrderCacheVersionService` | Redis-based cache version bumping for invalidation |
| Service | `ShippingFeeCalculator` | Vendor-based + international shipping fee computation |
| Repository | `OrderRepository` | PESSIMISTIC_WRITE `findByIdForUpdate`, Specification filtering, analytics queries |
| Repository | `VendorOrderRepository` | PESSIMISTIC_WRITE `findByIdForUpdate`, vendor-scoped queries |
| Repository | `OrderItemRepository` | Top products analytics |
| Repository | `OutboxEventRepository` | Pending event queries |
| Entity | `Order` | `@Version`, embedded address snapshots, OneToMany to `OrderItem` and `VendorOrder` |
| Entity | `VendorOrder` | `@Version`, per-vendor sub-order with tracking and refund info |
| Entity | `OrderItem` | Line item linking to both `Order` and `VendorOrder` |
| Entity | `OutboxEvent` | Outbox pattern entity — **no `@Version`** |
| Entity | `OrderStatus` | 14-state enum: PENDING → CLOSED |
| Client | `CustomerClient` | Customer lookup, address fetch (CB: customerService) |
| Client | `ProductClient` | Product lookup (CB: productService) |
| Client | `InventoryClient` | Stock check, reserve, confirm, release (CB: inventoryService) |
| Client | `PromotionClient` | Coupon commit, release (CB: promotionService) |
| Client | `VendorClient` | Vendor name lookup, user-to-vendor resolution (CB: vendorService) |
| Client | `VendorOperationalStateClient` | Vendor operational state batch check (CB: vendorService) |
| Filter | `OrderMutationIdempotencyFilter` | Redis-backed idempotency for POST/PATCH mutations |
| Scheduler | `OrderExpiryScheduler` | Cancel expired PENDING/PAYMENT_PENDING orders every 5 minutes |
| Scheduler | `OutboxProcessorScheduler` | Process outbox events every 5 seconds |

---

## BUG-ORD-001 — Order Expiry Scheduler Completely Broken (Three Compounding Issues)

| Field | Value |
|---|---|
| **Severity** | **HIGH** |
| **Category** | Data Integrity & Logic |
| **Affected Files** | `scheduler/OrderExpiryScheduler.java` |
| **Lines** | 43–91 |

### Description

The `OrderExpiryScheduler` is completely non-functional due to three compounding issues:

**Issue A — `LazyInitializationException`**: The scheduler loads orders in one transaction (`findExpiredOrders`), then processes each in a separate transaction via `transactionTemplate`. The `Order.vendorOrders` collection uses `FetchType.LAZY` (JPA default for `@OneToMany`). When `cancelExpiredOrder()` accesses `order.getVendorOrders()` at line 72, the lazy proxy is tied to the closed session from `findExpiredOrders` — this throws `LazyInitializationException`. Since ALL orders have vendor orders (created in `buildOrder`), this affects 100% of expired orders.

**Issue B — Missing Compensation Events**: Even if Issue A were fixed, `cancelExpiredOrder()` does NOT call `enqueueCompensationEvents(order, OrderStatus.CANCELLED)`. Compare with `OrderService.cancelMyOrder()` which correctly enqueues both `RELEASE_INVENTORY_RESERVATION` and `RELEASE_COUPON_RESERVATION` events. Without these compensation events:
- Inventory reservations for expired orders are **never released** — stock stays locked forever
- Coupon reservations for expired orders are **never freed** — coupons remain committed

**Issue C — No Pessimistic Lock / Stale State**: The scheduler reads orders without locking via `findExpiredOrders`, then saves the detached entity without re-loading with `findByIdForUpdate`. Between the read and the save, the order could have transitioned (e.g., payment confirmed). While `@Version` provides optimistic protection against overwriting concurrent modifications, the scheduler should re-verify the status is still cancellable.

The `catch (Exception ex)` at line 52 silently logs all three failures, hiding the bugs in production.

**Impact**: The 30-minute payment window is never enforced. Inventory reserved for abandoned orders is locked indefinitely. Coupons used in abandoned checkouts are never freed.

### Current Code

**`OrderExpiryScheduler.java:43–91`**:
```java
@Scheduled(fixedDelayString = "${order.expiry.check-interval:PT5M}")
public void cancelExpiredOrders() {
    List<Order> expired = orderRepository.findExpiredOrders(EXPIRABLE_STATUSES, Instant.now(), PageRequest.of(0, 500));
    if (expired.isEmpty()) {
        return;
    }
    log.info("Found {} expired orders to cancel", expired.size());
    for (Order order : expired) {
        try {
            transactionTemplate.executeWithoutResult(status -> cancelExpiredOrder(order));
        } catch (Exception ex) {
            log.error("Failed to cancel expired order {}: {}", order.getId(), ex.getMessage(), ex);
        }
    }
}

private void cancelExpiredOrder(Order order) {
    OrderStatus previousStatus = order.getStatus();
    order.setStatus(OrderStatus.CANCELLED);
    orderRepository.save(order);                     // Issue C: detached entity, no lock

    orderStatusAuditRepository.save(OrderStatusAudit.builder()
            .order(order)
            .fromStatus(previousStatus)
            .toStatus(OrderStatus.CANCELLED)
            .actorType("system")
            .changeSource("order_expired")
            .note("Order expired and auto-cancelled")
            .build());

    if (order.getVendorOrders() != null) {           // Issue A: LazyInitializationException here
        for (VendorOrder vo : order.getVendorOrders()) {
            // ... never reached
        }
    }
    // Issue B: no enqueueCompensationEvents() call
    log.info("Expired order {} cancelled (was {})", order.getId(), previousStatus);
}
```

### Fix

Replace `OrderExpiryScheduler.java:43–91` entirely:

```java
@Scheduled(fixedDelayString = "${order.expiry.check-interval:PT5M}")
public void cancelExpiredOrders() {
    List<Order> expired = orderRepository.findExpiredOrders(EXPIRABLE_STATUSES, Instant.now(), PageRequest.of(0, 500));
    if (expired.isEmpty()) {
        return;
    }
    log.info("Found {} expired orders to cancel", expired.size());
    for (Order order : expired) {
        try {
            transactionTemplate.executeWithoutResult(status -> cancelExpiredOrder(order.getId()));
        } catch (Exception ex) {
            log.error("Failed to cancel expired order {}: {}", order.getId(), ex.getMessage(), ex);
        }
    }
}

private void cancelExpiredOrder(UUID orderId) {
    // Fix C: Re-load with pessimistic lock inside the write transaction
    Order order = orderRepository.findByIdForUpdate(orderId)
            .orElse(null);
    if (order == null) {
        return;
    }
    // Re-verify the order is still in an expirable status
    if (!EXPIRABLE_STATUSES.contains(order.getStatus())) {
        log.debug("Order {} no longer in expirable status (now {}), skipping", orderId, order.getStatus());
        return;
    }

    OrderStatus previousStatus = order.getStatus();
    order.setStatus(OrderStatus.CANCELLED);
    orderRepository.save(order);

    orderStatusAuditRepository.save(OrderStatusAudit.builder()
            .order(order)
            .fromStatus(previousStatus)
            .toStatus(OrderStatus.CANCELLED)
            .actorType("system")
            .changeSource("order_expired")
            .note("Order expired and auto-cancelled")
            .build());

    // Fix A: vendorOrders are now accessible — loaded within the same session
    if (order.getVendorOrders() != null) {
        for (VendorOrder vo : order.getVendorOrders()) {
            if (vo.getStatus() != OrderStatus.CANCELLED) {
                OrderStatus voPrevious = vo.getStatus();
                vo.setStatus(OrderStatus.CANCELLED);
                vendorOrderRepository.save(vo);
                vendorOrderStatusAuditRepository.save(VendorOrderStatusAudit.builder()
                        .vendorOrder(vo)
                        .fromStatus(voPrevious)
                        .toStatus(OrderStatus.CANCELLED)
                        .actorType("system")
                        .changeSource("order_expired")
                        .note("Vendor order cancelled due to parent order expiry")
                        .build());
            }
        }
    }

    // Fix B: Enqueue compensation events to release inventory and coupon reservations
    enqueueCompensationEvents(order);

    log.info("Expired order {} cancelled (was {})", order.getId(), previousStatus);
}

private void enqueueCompensationEvents(Order order) {
    // Release coupon reservation
    if (order.getCouponReservationId() != null) {
        outboxEventRepository.save(OutboxEvent.builder()
                .aggregateType("Order")
                .aggregateId(order.getId())
                .eventType("RELEASE_COUPON_RESERVATION")
                .payload("{\"reservationId\":\"" + order.getCouponReservationId() + "\",\"reason\":\"order_expired\"}")
                .build());
    }
    // Release inventory reservation
    outboxEventRepository.save(OutboxEvent.builder()
            .aggregateType("Order")
            .aggregateId(order.getId())
            .eventType("RELEASE_INVENTORY_RESERVATION")
            .payload("{\"reason\":\"order_expired\"}")
            .build());
}
```

**Note**: This requires adding `OutboxEventRepository` to `OrderExpiryScheduler`'s constructor:

Replace `OrderExpiryScheduler.java:36–40`:
```java
private final OrderRepository orderRepository;
private final VendorOrderRepository vendorOrderRepository;
private final OrderStatusAuditRepository orderStatusAuditRepository;
private final VendorOrderStatusAuditRepository vendorOrderStatusAuditRepository;
private final TransactionTemplate transactionTemplate;
```

With:
```java
private final OrderRepository orderRepository;
private final VendorOrderRepository vendorOrderRepository;
private final OrderStatusAuditRepository orderStatusAuditRepository;
private final VendorOrderStatusAuditRepository vendorOrderStatusAuditRepository;
private final OutboxEventRepository outboxEventRepository;
private final TransactionTemplate transactionTemplate;
```

Add import:
```java
import com.rumal.order_service.entity.OutboxEvent;
import com.rumal.order_service.repo.OutboxEventRepository;
import java.util.UUID;
```

---

## BUG-ORD-002 — Resilience4j Retries Broken for 4 of 6 HTTP Clients

| Field | Value |
|---|---|
| **Severity** | **Medium** |
| **Category** | Architecture & Resilience |
| **Affected Files** | `client/CustomerClient.java`, `client/ProductClient.java`, `client/VendorClient.java`, `client/VendorOperationalStateClient.java` |
| **Lines** | `CustomerClient.java:48–49,67–68,89–90,109–110,128–129`, `ProductClient.java:39–40`, `VendorClient.java:57–58,80–81`, `VendorOperationalStateClient.java:61–62` |

### Description

The Resilience4j retry configuration specifies retryable exceptions:

```yaml
retryExceptions:
  - java.io.IOException
  - org.springframework.web.client.ResourceAccessException
  - org.springframework.web.client.HttpServerErrorException
```

Four clients catch `RestClientException` (the parent of both `ResourceAccessException` and `HttpServerErrorException`) and re-wrap it as `ServiceUnavailableException` **before** the exception reaches the Retry AOP aspect:

```java
// Pattern in CustomerClient, ProductClient, VendorClient, VendorOperationalStateClient
} catch (RestClientException ex) {
    throw new ServiceUnavailableException("Service unavailable: " + ex.getMessage(), ex);
}
```

Since `ServiceUnavailableException` is not in the `retryExceptions` list, the Retry aspect sees it and does **not** retry. All four clients have zero effective retries for transient failures.

**Not affected**: `InventoryClient` and `PromotionClient` only catch `HttpClientErrorException` (4xx errors), so 5xx and connectivity errors propagate naturally to the Retry aspect — their retries work correctly.

### Fix

Add `ServiceUnavailableException` to the retry configuration for all affected instances.

**`application.yaml`** — For each of `customerService`, `productService`, `vendorService`, add the service exception to `retryExceptions`:

Replace `application.yaml:112–118` (customerService retry):
```yaml
      customerService:
        maxAttempts: 3
        waitDuration: 300ms
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
        retryExceptions:
          - java.io.IOException
          - org.springframework.web.client.ResourceAccessException
          - org.springframework.web.client.HttpServerErrorException
          - com.rumal.order_service.exception.ServiceUnavailableException
```

Apply the same change to `productService` (lines 121–128) and `vendorService` (lines 130–138).

**Note**: `promotionService` and `inventoryService` retry configurations do NOT need this change because those clients don't catch `RestClientException` broadly.

---

## BUG-ORD-003 — Outbox Processor: Duplicate Event Dispatch in Multi-Pod Deployments

| Field | Value |
|---|---|
| **Severity** | **Medium** |
| **Category** | Data Integrity & Concurrency |
| **Affected Files** | `scheduler/OutboxProcessorScheduler.java`, `service/OutboxService.java`, `entity/OutboxEvent.java` |
| **Lines** | `OutboxProcessorScheduler.java:27–41`, `OutboxService.java:53–77`, `OutboxEvent.java` (entire entity) |

### Description

The `OutboxProcessorScheduler` reads PENDING events without any locking:

```java
List<OutboxEvent> events = outboxEventRepository
        .findByStatusAndNextRetryAtBeforeOrStatusAndNextRetryAtIsNull(
                OutboxEventStatus.PENDING, Instant.now(),
                OutboxEventStatus.PENDING,
                PageRequest.of(0, 50)
        );
```

The `OutboxEvent` entity has **no `@Version` field**, so there is no optimistic locking on event updates.

In a multi-pod deployment, concurrent scheduler instances can:
1. Both read the same PENDING event
2. Both call `dispatch(event)` — performing the downstream operation twice
3. Both update the event to PROCESSED — no conflict since there's no version check

For non-idempotent downstream operations, this causes:
- **`INVENTORY_RESERVE`**: Duplicate stock reservations for the same order (the inventory-service `reserveForOrder` also lacks idempotency per BUG-INV-002)
- **`COUPON_COMMIT`**: Duplicate coupon commits

### Current Code

**`OutboxProcessorScheduler.java:27–41`**:
```java
@Scheduled(fixedDelayString = "${outbox.processor.interval-ms:5000}")
public void processOutboxEvents() {
    try {
        List<OutboxEvent> events = outboxEventRepository
                .findByStatusAndNextRetryAtBeforeOrStatusAndNextRetryAtIsNull(
                        OutboxEventStatus.PENDING, Instant.now(),
                        OutboxEventStatus.PENDING,
                        PageRequest.of(0, 50)
                );
        for (OutboxEvent event : events) {
            outboxService.processEvent(event);
        }
    } catch (Exception ex) {
        log.error("Outbox processor failed", ex);
    }
}
```

### Fix

**Step 1** — Add `@Version` to `OutboxEvent` to prevent concurrent updates:

**`entity/OutboxEvent.java`** — Add after the `id` field (line 24):
```java
@Version
private Long version;
```

**Step 2** — Replace the repository query with a locking query using `SELECT ... FOR UPDATE SKIP LOCKED` to prevent concurrent processors from picking up the same events:

**`repo/OutboxEventRepository.java`** — Replace lines 14–18:
```java
List<OutboxEvent> findByStatusAndNextRetryAtBeforeOrStatusAndNextRetryAtIsNull(
        OutboxEventStatus status1, Instant before,
        OutboxEventStatus status2,
        Pageable pageable
);
```

With:
```java
@Query(value = """
        SELECT * FROM outbox_events
        WHERE status = 'PENDING'
          AND (next_retry_at IS NULL OR next_retry_at < :now)
        ORDER BY created_at ASC
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
List<OutboxEvent> findPendingEventsForProcessing(@Param("now") Instant now, @Param("limit") int limit);
```

**Step 3** — Update the scheduler to use the new query:

**`scheduler/OutboxProcessorScheduler.java`** — Replace lines 28–34:
```java
List<OutboxEvent> events = outboxEventRepository
        .findByStatusAndNextRetryAtBeforeOrStatusAndNextRetryAtIsNull(
                OutboxEventStatus.PENDING, Instant.now(),
                OutboxEventStatus.PENDING,
                PageRequest.of(0, 50)
        );
```

With:
```java
List<OutboxEvent> events = outboxEventRepository
        .findPendingEventsForProcessing(Instant.now(), 50);
```

**Step 4** — Wrap the scheduler method in a transaction so that `FOR UPDATE SKIP LOCKED` works correctly:

**`scheduler/OutboxProcessorScheduler.java`** — Add `TransactionTemplate` to the constructor and wrap the query:

Replace lines 27–41:
```java
@Scheduled(fixedDelayString = "${outbox.processor.interval-ms:5000}")
public void processOutboxEvents() {
    List<OutboxEvent> events;
    try {
        events = transactionTemplate.execute(status ->
                outboxEventRepository.findPendingEventsForProcessing(Instant.now(), 50));
    } catch (Exception ex) {
        log.error("Outbox processor query failed", ex);
        return;
    }
    if (events == null || events.isEmpty()) {
        return;
    }
    for (OutboxEvent event : events) {
        try {
            outboxService.processEvent(event);
        } catch (Exception ex) {
            log.error("Outbox processor failed for event {}", event.getId(), ex);
        }
    }
}
```

Add field:
```java
private final TransactionTemplate transactionTemplate;
```

---

## BUG-ORD-004 — CSV Export Customer Cache Re-Created Per Page

| Field | Value |
|---|---|
| **Severity** | **Low** |
| **Category** | Logic & Runtime |
| **Affected Files** | `service/OrderService.java` |
| **Lines** | 1546–1564 |

### Description

The `exportOrdersCsv()` method fetches customer details for each order page via individual REST calls. The customer cache is declared **inside** the page loop, so it is re-created on every iteration. The same customer is fetched once per page they appear in, rather than once total.

For a large export (up to 100K rows at page size 500 = 200 pages), frequent customers are re-fetched up to 200 times. Each fetch is an individual HTTP call to customer-service.

### Current Code

**`OrderService.java:1546–1564`**:
```java
do {
    orderPage = orderRepository.findAll(
            OrderSpecifications.withFilters(null, null, status, createdAfter, createdBefore),
            PageRequest.of(page, pageSize)
    );

    // Cache is inside the loop — re-created every page
    java.util.Map<UUID, CustomerSummary> customerCache = new java.util.HashMap<>();
    for (Order order : orderPage.getContent()) {
        UUID custId = order.getCustomerId();
        if (custId != null && !customerCache.containsKey(custId)) {
            try {
                customerCache.put(custId, customerClient.getCustomer(custId));
            } catch (Exception ex) {
                log.warn("Failed to fetch customer for CSV export, customerId={}", custId, ex);
                customerCache.put(custId, null);
            }
        }
    }
```

### Fix

Move the customer cache outside the page loop.

**`OrderService.java`** — Move the cache declaration before the `do` loop.

Replace `OrderService.java:1542–1553`:
```java
        try {
            writer.write("orderId,customerKeycloakId,customerEmail,status,grandTotal,currency,createdAt,updatedAt,itemCount\n");

            int page = 0;
            int pageSize = 500;
            int totalRowsWritten = 0;
            Page<Order> orderPage;
            do {
                orderPage = orderRepository.findAll(
                        OrderSpecifications.withFilters(null, null, status, createdAfter, createdBefore),
                        PageRequest.of(page, pageSize)
                );

                java.util.Map<UUID, CustomerSummary> customerCache = new java.util.HashMap<>();
```

With:
```java
        try {
            writer.write("orderId,customerKeycloakId,customerEmail,status,grandTotal,currency,createdAt,updatedAt,itemCount\n");

            int page = 0;
            int pageSize = 500;
            int totalRowsWritten = 0;
            Page<Order> orderPage;
            java.util.Map<UUID, CustomerSummary> customerCache = new java.util.HashMap<>();
            do {
                orderPage = orderRepository.findAll(
                        OrderSpecifications.withFilters(null, null, status, createdAfter, createdBefore),
                        PageRequest.of(page, pageSize)
                );
```

---

## Summary

| ID | Severity | Category | Title |
|---|---|---|---|
| BUG-ORD-001 | **HIGH** | Data Integrity & Logic | Order expiry scheduler broken — LazyInitializationException, missing compensation events, no lock |
| BUG-ORD-002 | Medium | Architecture & Resilience | Resilience4j retries broken for 4 of 6 HTTP clients |
| BUG-ORD-003 | Medium | Data Integrity & Concurrency | Outbox processor duplicate event dispatch in multi-pod deployments |
| BUG-ORD-004 | Low | Logic & Runtime | CSV export customer cache re-created per page |
