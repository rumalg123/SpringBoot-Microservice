# QA Bugs & Tasks — inventory-service

> **Auditor**: Principal Backend QA Architect & Senior Security Bug Hunter
> **Service**: `inventory-service` (port 8093)
> **Date**: 2026-02-27
> **Findings**: 8 total — 0 Critical, 2 High, 5 Medium, 1 Low

---

## Domain Map

| Layer | Component | Key Responsibility |
|---|---|---|
| Controller | `AdminStockController` (`/admin/inventory`) | Admin CRUD for stock items, adjustments, bulk import, movements, reservations |
| Controller | `AdminWarehouseController` (`/admin/inventory/warehouses`) | Admin CRUD for warehouses |
| Controller | `InternalInventoryController` (`/internal/inventory`) | Internal stock check, reserve, confirm, release, summary |
| Controller | `InternalInventoryAnalyticsController` (`/internal/inventory/analytics`) | Platform/vendor inventory health analytics |
| Controller | `VendorInventoryController` (`/inventory/vendor/me`) | Vendor self-service warehouse & stock management |
| Service | `StockService` | Core stock logic — availability checks, reserve/confirm/release, CRUD, adjust, bulk import, expiry |
| Service | `WarehouseService` | Warehouse CRUD for admin and vendor flows |
| Service | `AdminInventoryAccessScopeService` | RBAC scope resolution for admin/vendor roles |
| Service | `InventoryAnalyticsService` | Platform/vendor health summaries, low stock alerts |
| Repository | `StockItemRepository` | StockItem CRUD, pessimistic locking, filtered & analytics queries |
| Repository | `StockReservationRepository` | Reservation queries by order/status, expired reservation finder |
| Repository | `StockMovementRepository` | Audit trail queries with filters |
| Repository | `WarehouseRepository` | Warehouse CRUD with filtered queries |
| Entity | `StockItem` | `productId`, `vendorId`, `warehouse` (ManyToOne), `quantityOnHand/Reserved/Available`, `@Version` |
| Entity | `StockReservation` | `orderId`, `productId`, `stockItem` (ManyToOne), `quantityReserved`, `status`, `expiresAt`, `@Version` |
| Entity | `StockMovement` | Audit trail: `stockItem`, `movementType`, `quantityChange`, `before/after`, `actor` |
| Entity | `Warehouse` | `vendorId`, `warehouseType` (VENDOR_OWNED/PLATFORM_MANAGED), `active`, `@Version` |
| Client | `AccessClient` | Platform/vendor staff permission lookups (CB: accessService) |
| Client | `VendorAccessClient` | Vendor membership lookups (CB: vendorService) |
| Scheduler | `ReservationExpiryScheduler` | Expires stale reservations every 1 minute |
| Security | `InternalRequestVerifier` | HMAC-based internal request verification |
| Config | `SecurityConfig` | `permitAll()` — relies on API Gateway for auth |

---

## BUG-INV-001 — Admin Create/BulkImport Endpoints Missing Vendor Scope Validation

| Field | Value |
|---|---|
| **Severity** | **HIGH** |
| **Category** | Security & Access Control |
| **Affected Files** | `controller/AdminStockController.java`, `controller/AdminWarehouseController.java` |
| **Lines** | `AdminStockController.java:62–73, 105–115`, `AdminWarehouseController.java:62–73` |

### Description

The admin `createStockItem`, `bulkImport`, and warehouse `create` endpoints call `assertCanManageInventory(scope)` but do **not** validate that the `vendorId` in the request body belongs to the caller's vendor scope. This is correctly done for `updateStockItem`, `adjustStock`, warehouse `update`, and `updateStatus` — but the **create** paths skip it.

A **vendor_admin** or **vendor_staff** with `vendor.inventory.manage` permission can:

1. **Create stock items under another vendor's identity** by passing a foreign `vendorId` in the request body.
2. **Bulk import stock for arbitrary vendors** by setting any `vendorId` per item.
3. **Create warehouses attributed to other vendors** by passing a foreign `vendorId`.

This is a **cross-tenant data injection** vulnerability.

### Current Code

**`AdminStockController.java:62–73`** — No vendor scope check on create:
```java
@PostMapping("/stock")
@ResponseStatus(HttpStatus.CREATED)
public StockItemResponse createStockItem(
        @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
        @RequestHeader(value = "X-User-Sub", required = false) String userSub,
        @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
        @Valid @RequestBody StockItemCreateRequest request
) {
    AdminActorScope scope = resolveScope(internalAuth, userSub, userRoles);
    adminAccessScopeService.assertCanManageInventory(scope);
    return stockService.createStockItem(request);
}
```

**`AdminStockController.java:105–115`** — No vendor scope check on bulk import:
```java
@PostMapping("/stock/bulk-import")
public BulkStockImportResponse bulkImport(...) {
    AdminActorScope scope = resolveScope(internalAuth, userSub, userRoles);
    adminAccessScopeService.assertCanManageInventory(scope);
    return stockService.bulkImport(request.items(), "admin", userSub != null ? userSub : "unknown");
}
```

**`AdminWarehouseController.java:62–73`** — No vendor scope check on warehouse create:
```java
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public WarehouseResponse create(...) {
    AdminActorScope scope = resolveScope(internalAuth, userSub, userRoles);
    adminAccessScopeService.assertCanManageInventory(scope);
    return warehouseService.create(request);
}
```

Compare with `AdminStockController.updateStockItem` (lines 75–88) which **correctly** validates:
```java
StockItemResponse existing = stockService.getStockItem(id);
adminAccessScopeService.assertCanManageStockItem(scope, existing.vendorId());
```

### Fix

**Step 1** — In `AdminStockController.createStockItem`, add vendor scope validation.

Replace `AdminStockController.java:69–73`:
```java
    AdminActorScope scope = resolveScope(internalAuth, userSub, userRoles);
    adminAccessScopeService.assertCanManageInventory(scope);
    adminAccessScopeService.assertCanManageStockItem(scope, request.vendorId());
    return stockService.createStockItem(request);
}
```

**Step 2** — In `AdminStockController.bulkImport`, validate each item's vendorId.

Replace `AdminStockController.java:112–115`:
```java
    AdminActorScope scope = resolveScope(internalAuth, userSub, userRoles);
    adminAccessScopeService.assertCanManageInventory(scope);
    for (StockItemCreateRequest item : request.items()) {
        adminAccessScopeService.assertCanManageStockItem(scope, item.vendorId());
    }
    return stockService.bulkImport(request.items(), "admin", userSub != null ? userSub : "unknown");
```

**Step 3** — In `AdminWarehouseController.create`, add vendor scope validation.

Replace `AdminWarehouseController.java:69–73`:
```java
    AdminActorScope scope = resolveScope(internalAuth, userSub, userRoles);
    adminAccessScopeService.assertCanManageInventory(scope);
    adminAccessScopeService.assertCanManageWarehouse(scope, request.vendorId());
    return warehouseService.create(request);
}
```

---

## BUG-INV-002 — `reserveForOrder` Lacks Idempotency Guard — Duplicate Reservations on Retry

| Field | Value |
|---|---|
| **Severity** | **HIGH** |
| **Category** | Data Integrity & Concurrency |
| **Affected Files** | `service/StockService.java` |
| **Lines** | 58–140 |

### Description

The `reserveForOrder(orderId, items, expiresAt)` method creates `StockReservation` entries and decrements `quantityAvailable` on stock items, but it performs **no idempotency check** on the `orderId`. If the calling service (order-service) retries due to a timeout, network error, or Resilience4j retry, and the first call actually succeeded:

1. **Duplicate reservations are created** for the same orderId.
2. **`quantityReserved` is double-decremented**, reducing available stock by 2x the correct amount.
3. **`confirmReservation(orderId)`** later finds ALL reservations for that orderId and confirms them all, reducing `quantityOnHand` by double the correct amount.
4. This causes **phantom stock loss** — the system believes stock is depleted when it isn't.

This is a critical data integrity issue for the inventory management core.

### Current Code

**`StockService.java:58–60`** — No duplicate check:
```java
@Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
public StockReservationResponse reserveForOrder(UUID orderId, List<StockCheckRequest> items, Instant expiresAt) {
    List<ReservationItemResponse> reservationItems = new ArrayList<>();
```

### Fix

Add an idempotency check at the start of `reserveForOrder`.

Replace `StockService.java:58–62`:
```java
@Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
public StockReservationResponse reserveForOrder(UUID orderId, List<StockCheckRequest> items, Instant expiresAt) {
    List<StockReservation> existing = stockReservationRepository.findByOrderIdAndStatus(orderId, ReservationStatus.RESERVED);
    if (!existing.isEmpty()) {
        log.info("Reservation already exists for order {} — returning existing reservation (idempotent)", orderId);
        List<ReservationItemResponse> existingItems = existing.stream()
                .map(r -> new ReservationItemResponse(r.getId(), r.getProductId(),
                        r.getStockItem().getWarehouse().getId(), r.getQuantityReserved()))
                .toList();
        return new StockReservationResponse(orderId, "RESERVED", existingItems, existing.getFirst().getExpiresAt());
    }

    List<ReservationItemResponse> reservationItems = new ArrayList<>();
```

**Note**: Use `findByOrderIdAndStatusWithStockItem` instead of `findByOrderIdAndStatus` to avoid lazy-loading the warehouse in the response mapping:

Replace the idempotency check query:
```java
    List<StockReservation> existing = stockReservationRepository
            .findByOrderIdAndStatusWithStockItem(orderId, ReservationStatus.RESERVED);
```

---

## BUG-INV-003 — Admin List Endpoints Missing Vendor Scope — Cross-Tenant Information Disclosure

| Field | Value |
|---|---|
| **Severity** | **Medium** |
| **Category** | Security & Access Control |
| **Affected Files** | `controller/AdminStockController.java` |
| **Lines** | 117–127, 129–142, 144–156 |

### Description

Three admin list endpoints pass `assertCanManageInventory(scope)` (which only checks that the caller has *some* inventory access) but then query **all vendors' data** without applying the vendor scope filter:

| Endpoint | Line | Issue |
|---|---|---|
| `GET /admin/inventory/stock/low-stock` | 126 | Calls `listLowStock(pageable)` — returns all vendors |
| `GET /admin/inventory/movements` | 141 | Calls `listMovements(pageable, ...)` — no vendorId filter |
| `GET /admin/inventory/reservations` | 155 | Calls `listReservations(pageable, ...)` — no vendorId filter |

A **vendor_admin** or **vendor_staff** with inventory access can view low-stock alerts, stock movements, and reservations belonging to **all vendors** on the platform.

Compare with `GET /admin/inventory/stock` (line 44) which **correctly** applies vendor scoping via `resolveScopedVendorFilter`.

### Fix

**Step 1** — Add vendorId-scoped low stock method to `StockService`. Already exists: `listLowStockByVendor(UUID vendorId, Pageable pageable)`.

Replace `AdminStockController.java:117–127`:
```java
@GetMapping("/stock/low-stock")
public Page<StockItemResponse> lowStock(
        @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
        @RequestHeader(value = "X-User-Sub", required = false) String userSub,
        @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
) {
    AdminActorScope scope = resolveScope(internalAuth, userSub, userRoles);
    adminAccessScopeService.assertCanManageInventory(scope);
    if (scope.isPlatformPrivileged()) {
        return stockService.listLowStock(pageable);
    }
    UUID vendorId = adminAccessScopeService.resolveScopedVendorFilter(scope, null);
    return stockService.listLowStockByVendor(vendorId, pageable);
}
```

**Step 2** — Add vendorId parameter to movements query. Add to `StockService`:

```java
@Transactional(readOnly = true)
public Page<StockMovementResponse> listMovements(Pageable pageable, UUID vendorId, UUID productId, UUID warehouseId, MovementType movementType) {
    if (vendorId != null) {
        return stockMovementRepository.findFilteredByVendor(vendorId, productId, warehouseId, movementType, pageable)
                .map(this::toMovementResponse);
    }
    return stockMovementRepository.findFiltered(productId, warehouseId, movementType, pageable)
            .map(this::toMovementResponse);
}
```

Add to `StockMovementRepository`:
```java
@Query("""
        select m from StockMovement m
        where m.stockItem.vendorId = :vendorId
          and (:productId is null or m.productId = :productId)
          and (:warehouseId is null or m.warehouseId = :warehouseId)
          and (:movementType is null or m.movementType = :movementType)
        order by m.createdAt desc
        """)
Page<StockMovement> findFilteredByVendor(
        @Param("vendorId") UUID vendorId,
        @Param("productId") UUID productId,
        @Param("warehouseId") UUID warehouseId,
        @Param("movementType") MovementType movementType,
        Pageable pageable
);
```

Replace `AdminStockController.java:129–142`:
```java
@GetMapping("/movements")
public Page<StockMovementResponse> listMovements(
        @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
        @RequestHeader(value = "X-User-Sub", required = false) String userSub,
        @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
        @RequestParam(required = false) UUID productId,
        @RequestParam(required = false) UUID warehouseId,
        @RequestParam(required = false) MovementType movementType,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
) {
    AdminActorScope scope = resolveScope(internalAuth, userSub, userRoles);
    adminAccessScopeService.assertCanManageInventory(scope);
    UUID scopedVendorId = scope.isPlatformPrivileged() ? null : adminAccessScopeService.resolveScopedVendorFilter(scope, null);
    return stockService.listMovements(pageable, scopedVendorId, productId, warehouseId, movementType);
}
```

**Step 3** — Apply similar vendor scoping to reservations list. Add to `StockReservationRepository`:

```java
@Query("""
        select r from StockReservation r join fetch r.stockItem si
        where si.vendorId = :vendorId
          and (:status is null or r.status = :status)
          and (:orderId is null or r.orderId = :orderId)
        """)
Page<StockReservation> findFilteredByVendor(
        @Param("vendorId") UUID vendorId,
        @Param("status") ReservationStatus status,
        @Param("orderId") UUID orderId,
        Pageable pageable
);
```

Add to `StockService`:
```java
@Transactional(readOnly = true)
public Page<StockReservationDetailResponse> listReservations(Pageable pageable, UUID vendorId, ReservationStatus status, UUID orderId) {
    if (vendorId != null) {
        return stockReservationRepository.findFilteredByVendor(vendorId, status, orderId, pageable)
                .map(this::toReservationDetailResponse);
    }
    return stockReservationRepository.findFiltered(status, orderId, pageable)
            .map(this::toReservationDetailResponse);
}
```

Replace `AdminStockController.java:144–156`:
```java
@GetMapping("/reservations")
public Page<StockReservationDetailResponse> listReservations(
        @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
        @RequestHeader(value = "X-User-Sub", required = false) String userSub,
        @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
        @RequestParam(required = false) ReservationStatus status,
        @RequestParam(required = false) UUID orderId,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
) {
    AdminActorScope scope = resolveScope(internalAuth, userSub, userRoles);
    adminAccessScopeService.assertCanManageInventory(scope);
    UUID scopedVendorId = scope.isPlatformPrivileged() ? null : adminAccessScopeService.resolveScopedVendorFilter(scope, null);
    return stockService.listReservations(pageable, scopedVendorId, status, orderId);
}
```

---

## BUG-INV-004 — Resilience4j Retries Broken for Both HTTP Clients

| Field | Value |
|---|---|
| **Severity** | **Medium** |
| **Category** | Architecture & Resilience |
| **Affected Files** | `client/AccessClient.java`, `client/VendorAccessClient.java`, `application.yaml` |
| **Lines** | `AccessClient.java:42–46`, `VendorAccessClient.java:40–44`, `application.yaml:69–88` |

### Description

The Resilience4j retry configuration specifies retryable exceptions:

```yaml
retryExceptions:
  - java.io.IOException
  - org.springframework.web.client.ResourceAccessException
  - org.springframework.web.client.HttpServerErrorException
```

However, both `AccessClient` and `VendorAccessClient` catch `RestClientException` (the parent of `ResourceAccessException` and `HttpServerErrorException`) and **re-wrap** it as `ServiceUnavailableException` **before** the exception reaches the Retry AOP aspect:

```java
// AccessClient.java:42–46
} catch (RestClientResponseException ex) {
    throw new ServiceUnavailableException("Access service platform lookup failed (" + ex.getStatusCode().value() + ")", ex);
} catch (RestClientException | IllegalStateException ex) {
    throw new ServiceUnavailableException("Access service unavailable for platform lookup", ex);
}
```

Since `ServiceUnavailableException` is **not** in the `retryExceptions` list, the Retry aspect does **not** retry. Both clients exhibit this pattern. Transient connectivity failures and 5xx responses immediately surface as errors with zero retries.

### Fix

Add `ServiceUnavailableException` to the retry configuration for both instances.

Replace `application.yaml:69–88`:
```yaml
  retry:
    instances:
      accessService:
        maxAttempts: 3
        waitDuration: 300ms
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
        retryExceptions:
          - java.io.IOException
          - org.springframework.web.client.ResourceAccessException
          - org.springframework.web.client.HttpServerErrorException
          - com.rumal.inventory_service.exception.ServiceUnavailableException
      vendorService:
        maxAttempts: 3
        waitDuration: 300ms
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
        retryExceptions:
          - java.io.IOException
          - org.springframework.web.client.ResourceAccessException
          - org.springframework.web.client.HttpServerErrorException
          - com.rumal.inventory_service.exception.ServiceUnavailableException
```

---

## BUG-INV-005 — `expireStaleReservations()` Unbounded Work in Single Transaction

| Field | Value |
|---|---|
| **Severity** | **Medium** |
| **Category** | Architecture & Resilience |
| **Affected Files** | `service/StockService.java`, `scheduler/ReservationExpiryScheduler.java` |
| **Lines** | `StockService.java:417–458` |

### Description

The `expireStaleReservations()` method processes **all** expired reservations in a single `REPEATABLE_READ` transaction with a 20-second timeout:

```java
@Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
public int expireStaleReservations() {
    // ...
    do {
        page = stockReservationRepository
                .findExpiredReservations(ReservationStatus.RESERVED, now, PageRequest.of(0, 100));
        for (StockReservation reservation : page.getContent()) {
            // Lock StockItem, update quantities, save, record movement
        }
        totalExpired += page.getNumberOfElements();
    } while (!page.isEmpty());
    // ...
}
```

If there are thousands of expired reservations (e.g., after a surge of abandoned checkouts):

1. The do-while loop iterates through all pages within one transaction.
2. Each iteration acquires PESSIMISTIC_WRITE locks on StockItem rows.
3. Locks accumulate across pages — the transaction holds locks on ALL processed rows until commit.
4. The 20-second timeout causes a **total rollback** of all work.
5. The scheduler retries 1 minute later and faces the same problem — **no forward progress**.

### Fix

Move the transactional boundary to a per-batch method using a separate Spring bean to avoid proxy self-invocation issues.

**Step 1** — Extract a per-batch method in `StockService`:

Replace `StockService.java:417–458`:
```java
@Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
public int expireStaleReservationsBatch(int batchSize) {
    Instant now = Instant.now();
    Page<StockReservation> page = stockReservationRepository
            .findExpiredReservations(ReservationStatus.RESERVED, now, PageRequest.of(0, batchSize));

    for (StockReservation reservation : page.getContent()) {
        StockItem stockItem = stockItemRepository.findByIdForUpdate(reservation.getStockItem().getId())
                .orElse(null);
        if (stockItem == null) {
            log.warn("StockItem {} not found during expiry of reservation {}", reservation.getStockItem().getId(), reservation.getId());
            continue;
        }

        int quantityBefore = stockItem.getQuantityAvailable();
        stockItem.setQuantityReserved(stockItem.getQuantityReserved() - reservation.getQuantityReserved());
        stockItem.recalculateAvailable();
        stockItem.recalculateStatus();
        stockItemRepository.save(stockItem);

        reservation.setStatus(ReservationStatus.EXPIRED);
        reservation.setReleasedAt(now);
        reservation.setReleaseReason("Reservation expired");
        stockReservationRepository.save(reservation);

        recordMovement(stockItem, MovementType.RESERVATION_RELEASE, reservation.getQuantityReserved(),
                quantityBefore, stockItem.getQuantityAvailable(),
                "order", reservation.getOrderId(), "system", "scheduler",
                "Reservation expired for order " + reservation.getOrderId());
    }

    int expired = page.getNumberOfElements();
    if (expired > 0) {
        log.info("Expired {} stale reservations (batch)", expired);
    }
    return expired;
}
```

**Step 2** — Update `ReservationExpiryScheduler` to loop over batches:

Replace `ReservationExpiryScheduler.java:19–28`:
```java
@Scheduled(fixedDelayString = "${inventory.reservation.cleanup-interval:PT1M}", initialDelayString = "PT30S")
public void cleanupExpiredReservations() {
    int totalExpired = 0;
    try {
        int batch;
        do {
            batch = stockService.expireStaleReservationsBatch(100);
            totalExpired += batch;
        } while (batch > 0);
    } catch (Exception e) {
        log.error("Error during reservation expiry cleanup (expired {} so far)", totalExpired, e);
    }
    if (totalExpired > 0) {
        log.info("Reservation expiry scheduler released {} expired reservations total", totalExpired);
    }
}
```

Each batch now commits independently. If one batch fails, previously committed batches are preserved.

---

## BUG-INV-006 — `bulkImport()` Error Handling Broken Within Single Transaction

| Field | Value |
|---|---|
| **Severity** | **Medium** |
| **Category** | Data Integrity & Concurrency |
| **Affected Files** | `service/StockService.java` |
| **Lines** | 324–381 |

### Description

The `bulkImport()` method runs within a single `@Transactional(isolation = REPEATABLE_READ)` but uses a per-item `catch (Exception e)` to collect errors:

```java
@Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
public BulkStockImportResponse bulkImport(List<StockItemCreateRequest> items, String actorType, String actorId) {
    // ...
    for (int i = 0; i < items.size(); i++) {
        try {
            // persist stock item + movement
            created++;
        } catch (Exception e) {
            errors.add("Item[" + i + "] productId=" + req.productId() + ": " + e.getMessage());
        }
    }
    return new BulkStockImportResponse(items.size(), created, updated, errors);
}
```

This has two problems:

1. **Hibernate session corruption**: If a JPA exception (e.g., `DataIntegrityViolationException`) occurs during `save()`, the Hibernate persistence context becomes invalid and the transaction is marked rollback-only. The `catch` block prevents propagation, but subsequent `save()` calls in later iterations will fail.

2. **Misleading response**: The method returns a response showing N items created and M updated, but since the transaction is rollback-only, **all changes are rolled back on commit**. The caller believes items were persisted when they weren't.

Additionally, each iteration's `findByProductIdAndWarehouseId` query triggers a Hibernate flush, which can surface constraint violations from the *previous* iteration — misattributing errors.

### Fix

Use Spring's `TransactionTemplate` with `REQUIRES_NEW` propagation for each item, preserving partial success.

**Step 1** — Inject `PlatformTransactionManager` into `StockService`:

Add to `StockService.java` fields:
```java
private final org.springframework.transaction.PlatformTransactionManager txManager;
```

**Step 2** — Replace `bulkImport` to use per-item transactions:

Replace `StockService.java:324–381`:
```java
public BulkStockImportResponse bulkImport(List<StockItemCreateRequest> items, String actorType, String actorId) {
    int created = 0;
    int updated = 0;
    List<String> errors = new ArrayList<>();

    var txTemplate = new org.springframework.transaction.support.TransactionTemplate(txManager);
    txTemplate.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
    txTemplate.setTimeout(10);

    for (int i = 0; i < items.size(); i++) {
        StockItemCreateRequest req = items.get(i);
        final int index = i;
        try {
            String result = txTemplate.execute(status -> {
                Optional<StockItem> existing = stockItemRepository.findByProductIdAndWarehouseId(req.productId(), req.warehouseId());
                if (existing.isPresent()) {
                    StockItem stockItem = existing.get();
                    int quantityBefore = stockItem.getQuantityAvailable();
                    int diff = req.quantityOnHand() - stockItem.getQuantityOnHand();
                    stockItem.setQuantityOnHand(req.quantityOnHand());
                    stockItem.setSku(req.sku());
                    stockItem.setLowStockThreshold(req.lowStockThreshold());
                    stockItem.setBackorderable(req.backorderable());
                    stockItem.recalculateAvailable();
                    stockItem.recalculateStatus();
                    stockItemRepository.save(stockItem);

                    if (diff != 0) {
                        recordMovement(stockItem, MovementType.BULK_IMPORT, diff,
                                quantityBefore, stockItem.getQuantityAvailable(),
                                "bulk_import", null, actorType, actorId, "Bulk import update");
                    }
                    return "updated";
                } else {
                    Warehouse warehouse = warehouseService.findById(req.warehouseId());
                    StockItem stockItem = StockItem.builder()
                            .productId(req.productId())
                            .vendorId(req.vendorId())
                            .warehouse(warehouse)
                            .sku(req.sku())
                            .quantityOnHand(req.quantityOnHand())
                            .quantityReserved(0)
                            .quantityAvailable(req.quantityOnHand())
                            .lowStockThreshold(req.lowStockThreshold())
                            .backorderable(req.backorderable())
                            .build();
                    stockItem.recalculateStatus();
                    stockItem = stockItemRepository.save(stockItem);

                    if (req.quantityOnHand() > 0) {
                        recordMovement(stockItem, MovementType.BULK_IMPORT, req.quantityOnHand(),
                                0, req.quantityOnHand(),
                                "bulk_import", null, actorType, actorId, "Bulk import create");
                    }
                    return "created";
                }
            });
            if ("created".equals(result)) created++;
            else updated++;
        } catch (Exception e) {
            errors.add("Item[" + index + "] productId=" + req.productId() + ": " + e.getMessage());
        }
    }

    return new BulkStockImportResponse(items.size(), created, updated, errors);
}
```

Remove the `@Transactional` annotation from this method since it now manages transactions internally.

---

## BUG-INV-007 — StockItem Creation Missing Warehouse-Vendor Ownership Validation

| Field | Value |
|---|---|
| **Severity** | **Medium** |
| **Category** | Security & Access Control |
| **Affected Files** | `service/StockService.java` |
| **Lines** | 254–287 |

### Description

`StockService.createStockItem()` fetches the warehouse but does **not** verify that the warehouse belongs to the requesting vendor:

```java
@Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
public StockItemResponse createStockItem(StockItemCreateRequest request) {
    Warehouse warehouse = warehouseService.findById(request.warehouseId());
    // No check: warehouse.getVendorId() == request.vendorId()
    // ...
}
```

This allows:

1. **Via vendor endpoint** (`VendorInventoryController.createStockItem`): A vendor can create stock items linked to **another vendor's warehouse** by passing a foreign `warehouseId` in the request body. The vendorId is correctly enforced from the header, but the warehouse association is unchecked.

2. **Via admin endpoint** (`AdminStockController.createStockItem`): Same issue — the warehouse-vendor relationship is not validated.

A stock item linked to the wrong warehouse corrupts inventory tracking — the physical location doesn't match the system record.

### Fix

Add warehouse-vendor ownership validation in `StockService.createStockItem`.

Replace `StockService.java:256–262`:
```java
    Warehouse warehouse = warehouseService.findById(request.warehouseId());

    if (warehouse.getVendorId() != null && !warehouse.getVendorId().equals(request.vendorId())) {
        throw new ValidationException("Warehouse " + request.warehouseId()
                + " does not belong to vendor " + request.vendorId());
    }

    stockItemRepository.findByProductIdAndWarehouseId(request.productId(), request.warehouseId())
```

The `warehouse.getVendorId() != null` check allows PLATFORM_MANAGED warehouses (which have `vendorId = null`) to be used by any vendor.

---

## BUG-INV-008 — `checkAvailability()` N+1 Lazy Loading on Warehouse

| Field | Value |
|---|---|
| **Severity** | **Low** |
| **Category** | Architecture & Resilience |
| **Affected Files** | `service/StockService.java`, `repo/StockItemRepository.java` |
| **Lines** | `StockService.java:40–54`, `StockItemRepository.java:21` |

### Description

The `checkAvailability()` method uses `findByProductId` (a derived query without `join fetch`) and then accesses the lazy-loaded `warehouse` relationship to filter by active status:

```java
public List<StockCheckResult> checkAvailability(List<StockCheckRequest> requests) {
    // ...
    for (StockCheckRequest req : requests) {
        List<StockItem> items = stockItemRepository.findByProductId(req.productId());
        int totalAvailable = items.stream()
                .filter(s -> s.getWarehouse().isActive())  // ← lazy load per item
                .mapToInt(StockItem::getQuantityAvailable)
                .sum();
```

For each `StockItem`, `s.getWarehouse().isActive()` triggers a separate SQL query to load the `Warehouse` entity. If a product has N stock items across N warehouses, this results in N+1 queries per product checked.

This endpoint is called by internal services (e.g., cart-service during checkout preview) and can be invoked with multiple products via the batch.

### Fix

Add a new repository method with `join fetch` for the warehouse, filtering by active warehouses.

**Step 1** — Add to `StockItemRepository.java`:
```java
@Query("select s from StockItem s join fetch s.warehouse w where s.productId = :productId and w.active = true")
List<StockItem> findByProductIdWithActiveWarehouse(@Param("productId") UUID productId);
```

**Step 2** — Update `checkAvailability` to use the new query.

Replace `StockService.java:42–48`:
```java
    for (StockCheckRequest req : requests) {
        List<StockItem> items = stockItemRepository.findByProductIdWithActiveWarehouse(req.productId());
        int totalAvailable = items.stream()
                .mapToInt(StockItem::getQuantityAvailable)
                .sum();
        boolean backorderable = items.stream().anyMatch(StockItem::isBackorderable);
```

The `where w.active = true` clause in the query replaces the Java-side `.filter(s -> s.getWarehouse().isActive())`, eliminating N+1 queries.

---

## Summary

| ID | Severity | Category | Title |
|---|---|---|---|
| BUG-INV-001 | **HIGH** | Security & Access Control | Admin create/bulk-import endpoints missing vendor scope validation |
| BUG-INV-002 | **HIGH** | Data Integrity & Concurrency | `reserveForOrder` lacks idempotency guard — duplicate reservations on retry |
| BUG-INV-003 | Medium | Security & Access Control | Admin list endpoints missing vendor scope — cross-tenant data leak |
| BUG-INV-004 | Medium | Architecture & Resilience | Resilience4j retries broken — `ServiceUnavailableException` not retryable |
| BUG-INV-005 | Medium | Architecture & Resilience | `expireStaleReservations()` unbounded work in single transaction |
| BUG-INV-006 | Medium | Data Integrity & Concurrency | `bulkImport()` error handling broken within single transaction |
| BUG-INV-007 | Medium | Security & Access Control | StockItem creation missing warehouse-vendor ownership validation |
| BUG-INV-008 | Low | Architecture & Resilience | `checkAvailability()` N+1 lazy loading on warehouse |
