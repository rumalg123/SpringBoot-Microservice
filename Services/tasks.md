# Audit Findings & Implementation Tasks

## CROSS-SERVICE: Port Conflicts [CRITICAL]

- [x] **search-service port 8090 conflicts with vendor-service port 8090**
  - `search-service/src/main/resources/application.yaml`: changed to `port: 8094`
- [x] **personalization-service port 8089 conflicts with poster-service port 8089**
  - `poster-service/src/main/resources/application.yaml`: changed to `port: 8095`
- [x] **access-service port 8091 conflicts with promotion-service port 8091**
  - `access-service/src/main/resources/application.yaml`: changed to `port: 8096`
- [x] **api-gateway routes** - no changes needed, gateway uses `lb://service-name` (Eureka discovery)

---

## CROSS-SERVICE: Missing @Version (Optimistic Locking)

- [x] **customer-service: CustomerAddress** - added `@Version private Long version;`
- [x] **wishlist-service: WishlistCollection** - added `@Version private Long version;`
- [x] **wishlist-service: WishlistItem** - added `@Version private Long version;`
- [x] **promotion-service: CouponReservation** - added `@Version private Long version;`
- [x] **poster-service: PosterVariant** - added `@Version private Long version;`
- [x] **inventory-service: StockReservation** - added `@Version private Long version;`
- [x] **review-service: ReviewReport** - added `@Version private Long version;`
- [x] **access-service: PlatformStaffAccess** - added `@Version private Long version;`
- [x] **access-service: VendorStaffAccess** - added `@Version private Long version;`
- [x] **product-service: Category** - added `@Version private Long version;`
- N/A **review-service: ReviewVote** - insert-only entity, @Version not applicable
- N/A **access-service: PermissionGroup** - immutable entity (no updatedAt), @Version not applicable
- N/A **access-service: ApiKey** - immutable entity (no updatedAt), @Version not applicable
- N/A **personalization-service: UserAffinity** - composite @IdClass, @Version not applicable

---

## payment-service

- [x] **Security: initiatePayment doesn't verify keycloakId matches order's customer**
  - Added ownership check: fetch customer by keycloakId, compare to order.customerId(), throw UnauthorizedException on mismatch
- [x] **Missing @Transactional(readOnly=true) on PaymentService query methods**
  - Added to: getPaymentById, getPaymentByOrder, getPaymentDetail, listPaymentsForCustomer, listAllPayments, getAuditTrail
- [x] **Missing @Transactional(readOnly=true) on RefundService query methods**
  - Added to: listRefundsForCustomer, listRefundsForVendor, listAllRefunds, getRefundById, getRefundByIdAndCustomer, getRefundByIdAndVendor
- [x] **Missing @Transactional(readOnly=true) on PayoutService query methods**
  - Added to: listPayouts, getPayoutById
- [x] **Unbounded query: findOrderSyncPending**
  - Changed to `Page<Payment>` with Pageable. Updated OrderSyncRetryScheduler to process in batches of 100.
- [x] **Unbounded query: findByStatusAndExpiresAtBefore**
  - Changed to `Page<Payment>` with Pageable. Updated PaymentExpiryScheduler to process in batches of 100.
- [x] **Unbounded query: escalateExpiredRefunds**
  - Changed repo method to `Page<RefundRequest>` with Pageable. Updated RefundService to process in batches of 100.
- [x] **Code duplication: writeAudit**
  - Extracted into shared `PaymentAuditService`. Injected into PaymentService, RefundService, PayoutService. Removed private writeAudit from all three.

---

## inventory-service

- [x] **Unbounded query: expireStaleReservations**
  - Changed repo `findExpiredReservations` to `Page<StockReservation>` with Pageable (with separate countQuery for join fetch).
  - Updated StockService.expireStaleReservations() to process in batches of 100.

---

## order-service

- [ ] **Unbounded query: exportOrdersCsv**
  - `order-service/.../service/OrderService.java`: `exportOrdersCsv` should stream results using `@QueryHints` with `FETCH_SIZE` or process in pages instead of loading all results into memory.

---

## promotion-service

- [x] **CouponReservation missing @Version** (done in cross-service section)
- [x] **Missing @Transactional(readOnly=true) on CouponValidationService**
  - Added class-level `@Transactional(readOnly = true)`

---

## personalization-service

- [x] **Unbounded queries in UserEventRepository**
  - Changed return types from `List<UserEvent>` to `Page<UserEvent>` (methods already had Pageable param)

---

## poster-service

- [x] **Port conflict** (done in cross-service section)
- [x] **PosterVariant missing @Version** (done in cross-service section)
- [x] **Unbounded query: findByDeletedTrueOrderByUpdatedAtDesc**
  - Removed unbounded List overload, kept only Page overload. Updated PosterServiceImpl.listDeleted().

---

## admin-service

- [ ] **Unbounded query: SystemConfigService findAll**
  - Low priority: config entries are few. Acceptable as-is.

---

## analytics-service

- [x] **Missing timeout on CompletableFuture calls**
  - Added `.orTimeout(10, TimeUnit.SECONDS)` to all 25 parallel CompletableFuture calls across 8 methods.
- [x] **Missing @RequestParam validation on periodDays**
  - Added `@Min(1) @Max(365)` to periodDays/days params. Added `@Validated` to controller class.
