# Audit Findings & Implementation Tasks

## Legend
- [x] = Completed (from prior audit)
- [ ] = To implement

---

## CROSS-SERVICE: Port Conflicts [COMPLETED]

- [x] search-service port 8090 → 8094
- [x] poster-service port 8089 → 8095
- [x] access-service port 8091 → 8096

---

## CROSS-SERVICE: Missing @Version (Optimistic Locking) [COMPLETED]

- [x] customer-service: CustomerAddress
- [x] wishlist-service: WishlistCollection, WishlistItem
- [x] promotion-service: CouponReservation
- [x] poster-service: PosterVariant
- [x] inventory-service: StockReservation
- [x] review-service: ReviewReport
- [x] access-service: PlatformStaffAccess, VendorStaffAccess
- [x] product-service: Category

---

## CROSS-SERVICE: Previously Completed Tasks [COMPLETED]

- [x] payment-service: ownership check in initiatePayment
- [x] payment-service: @Transactional(readOnly=true) on all query methods
- [x] payment-service: paginated schedulers (expiry, order sync retry, refund escalation)
- [x] payment-service: extracted PaymentAuditService
- [x] inventory-service: paginated expireStaleReservations
- [x] promotion-service: @Transactional(readOnly=true) on CouponValidationService
- [x] personalization-service: paginated UserEventRepository
- [x] poster-service: removed unbounded findByDeletedTrue List overload
- [x] analytics-service: CompletableFuture .orTimeout(10, SECONDS) on all calls
- [x] analytics-service: @Min(1) @Max(365) on periodDays/days params

---
---

# NEW TASKS

---

## P0-01: API Gateway — Enforce Audience Validation [COMPLETED]

**Issue:** `AudienceValidator.validate()` returns `success()` when `requiredAudiences` is empty, accepting ANY JWT token from any Keycloak client/realm.

- **File:** `api-gateway/.../config/AudienceValidator.java` (line 32-33)
  - Change: when `requiredAudiences.isEmpty()`, return `OAuth2TokenValidatorResult.failure(...)` with error "Audience configuration is required but not set"
- **File:** `api-gateway/.../config/SecurityConfig.java`
  - Change: add a startup validation (`@PostConstruct` or `InitializingBean`) that fails fast if `keycloak.audience` property is blank/missing. Log a clear error: "FATAL: keycloak.audience must be configured"

---

## P0-02: API Gateway — Fix IP Spoofing in IpFilterConfig, AccessLoggingFilter, and RateLimitConfig [COMPLETED]

**Issue:** `resolveClientIp()` in `IpFilterConfig` and `AccessLoggingFilter` trusts `CF-Connecting-IP` and `X-Forwarded-For` headers unconditionally WITHOUT checking whether the direct connection is from a trusted proxy. `RateLimitConfig` already has `isTrustedProxy()` — the other two do not.

- **File:** `api-gateway/.../config/IpFilterConfig.java` (lines 78-91)
  - Change: add the same `isTrustedProxy(remoteIp)` check that `RateLimitConfig` uses before reading forwarded headers. If the direct remote IP is NOT in the trusted proxy list, use the direct remote address.
- **File:** `api-gateway/.../config/AccessLoggingFilter.java` (lines 60-73)
  - Change: same fix — add `isTrustedProxy()` check before trusting `CF-Connecting-IP` / `X-Forwarded-For`.
- **File:** `api-gateway/.../config/IpFilterConfig.java` (line 57)
  - Change: when `allowlistEnabled && allowedIps.isEmpty()`, deny ALL traffic (fail-closed) instead of allowing all. Log a WARN: "IP allowlist enabled but no IPs configured — blocking all traffic".

---

## P0-03: Promotion Service — Paginate Active Promotion Loading in Quote [COMPLETED]

**Issue:** `PromotionQuoteService.quote()` calls `findByLifecycleStatusAndApprovalStatusIn()` which returns an unbounded `List<PromotionCampaign>`. With thousands of active promotions, this OOMs.

- **File:** `promotion-service/.../repo/PromotionCampaignRepository.java` (line 21-24)
  - Change: add a paginated overload: `Page<PromotionCampaign> findByLifecycleStatusAndApprovalStatusIn(PromotionLifecycleStatus, Collection<PromotionApprovalStatus>, Pageable)`
- **File:** `promotion-service/.../service/PromotionQuoteService.java` (line 88-95)
  - Change: replace the single `findByLifecycleStatusAndApprovalStatusIn()` call with a loop that fetches pages (e.g., 200 per page) and accumulates candidates. Apply the `withinWindow`, `matchesSegment`, `withinFlashSaleWindow` filters as each page is streamed, to keep memory footprint low.

---

## P0-04: Promotion Service — Fix Cache Key Missing Pagination Params [COMPLETED]

**Issue:** `PromotionCampaignService.list()` uses `@Cacheable(cacheNames = "promotionAdminList")` without including pagination params in the cache key. Different pages return the same cached page 0.

- **File:** `promotion-service/.../service/PromotionCampaignService.java` (the `@Cacheable` annotation on the `list` method)
  - Change: update cache key to include the pagination and filter params, e.g. `key = "T{#root.methodName}-P{#pageable.pageNumber}-S{#pageable.pageSize}-SO{#pageable.sort}"`
  - If method has additional filter parameters, include them in the key too.

---

## P0-05: Cart Service — Batch Cart Expiry Deletion [COMPLETED]

**Issue:** `CartExpiryScheduler.purgeExpiredCarts()` calls `deleteExpiredCarts(cutoff)` as a single unbounded DELETE. With millions of cart rows, this locks the table for an extended period and can timeout.

- **File:** `cart-service/.../repo/CartRepository.java` (line 24-26)
  - Change: add a new method that deletes in limited batches:
    ```java
    @Modifying
    @Query(value = "DELETE FROM carts WHERE id IN (SELECT id FROM carts WHERE last_activity_at < :cutoff LIMIT :batchSize)", nativeQuery = true)
    int deleteExpiredCartsBatch(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);
    ```
- **File:** `cart-service/.../scheduler/CartExpiryScheduler.java` (line 29-39)
  - Change: replace single `deleteExpiredCarts(cutoff)` call with a loop that calls `deleteExpiredCartsBatch(cutoff, 500)` repeatedly until the returned count is 0.
  - Remove the `@Transactional` from the scheduler method. Each batch delete is its own transaction.
  - Add a configurable batch size: `@Value("${cart.expiry.batch-size:500}")`

---

## P0-06: Order Service — CSV Export Streaming + Batch Customer Lookup [COMPLETED]

**Issue:** `exportOrdersCsv()` loads all matching orders into a `StringBuilder` in memory, and makes N+1 HTTP calls to customer-service (one per order).

- **File:** `order-service/.../service/OrderService.java` (line 1508-1543)
  - Change `exportOrdersCsv` to accept a `Writer` or `OutputStream` parameter and write CSV rows incrementally instead of building a `StringBuilder`.
  - Batch customer lookups: for each page of 500 orders, collect all unique `customerId`s, make a single batch call to customer-service (or add a batch endpoint if missing), then build a `Map<UUID, CustomerSummary>` for O(1) lookup per order.
  - Add CSV injection protection: prefix cell values starting with `=`, `+`, `-`, `@`, `\t`, `\r` with a single quote `'` in `escapeCsvField()`.
- **File:** `order-service/.../controller/AdminOrderController.java` (the endpoint that calls exportOrdersCsv)
  - Change: return `StreamingResponseBody` or write directly to `HttpServletResponse.getOutputStream()` to avoid holding the full CSV in memory.

---

## P0-07: Order Service — Transactional Outbox for Compensation [COMPLETED]

**Issue:** `afterCommit()` callbacks for coupon release and inventory release are fire-and-forget. If they fail, the system is left in an inconsistent state with no retry.

- **File:** `order-service/.../entity/` — create a new entity `OutboxEvent.java`:
  - Fields: `id` (UUID), `aggregateType` (String), `aggregateId` (UUID), `eventType` (String), `payload` (JSON String), `status` (PENDING/PROCESSED/FAILED), `retryCount` (int), `maxRetries` (int, default 5), `createdAt`, `processedAt`, `nextRetryAt`
- **File:** `order-service/.../repo/` — create `OutboxEventRepository.java`
- **File:** `order-service/.../service/OutboxService.java` — create:
  - `enqueue(aggregateType, aggregateId, eventType, payload)`: inserts new OutboxEvent within the SAME transaction as the order status change
  - `processEvent(OutboxEvent)`: dispatches to the correct client call (coupon, inventory) with idempotency
- **File:** `order-service/.../scheduler/OutboxProcessorScheduler.java` — create:
  - `@Scheduled(fixedDelay = 5000)`: fetches PENDING outbox events in batches of 50, calls `outboxService.processEvent()` for each
- **File:** `order-service/.../service/OrderService.java`:
  - Replace `afterCommit()` callbacks with `outboxService.enqueue(...)` calls within the same `@Transactional` method.

---

## P0-08: Access Service — restorePlatformStaff and restoreVendorStaff Don't Re-Activate Records [COMPLETED]

**Issue:** `softDeletePlatformStaff` sets both `deleted=true` and `active=false`. But `restorePlatformStaff` only sets `deleted=false` and `deletedAt=null` — it NEVER sets `active=true`. Restored users remain permanently locked out. Same bug in `restoreVendorStaff`.

- **File:** `access-service/.../service/AccessServiceImpl.java` (lines 276-277)
  - Change: add `staff.setActive(true);` after `staff.setDeleted(false);` in `restorePlatformStaff`.
- **File:** `access-service/.../service/AccessServiceImpl.java` (lines 439-457, in `restoreVendorStaff`)
  - Change: add `staff.setActive(true);` after `staff.setDeleted(false);` in `restoreVendorStaff`.

---

## P0-09: Review Service — Missing `import jakarta.persistence.Version` (Compilation Error) [COMPLETED]

**Issue:** `ReviewReport.java` uses `@Version` annotation at line 48 but does not import `jakarta.persistence.Version`. This causes a compilation failure.

- **File:** `review-service/.../entity/ReviewReport.java`
  - Change: add `import jakarta.persistence.Version;` to the imports section.

---

## P0-10: Review Service — Missing @EnableCaching [COMPLETED]

**Issue:** `ReviewServiceApplication.java` has no `@EnableCaching` annotation, and no `@Configuration` class enables it. All `@Cacheable`/`@CacheEvict` annotations in `ReviewServiceImpl` are silently no-ops.

- **File:** `review-service/.../ReviewServiceApplication.java`
  - Change: add `@EnableCaching` annotation to the class.

---

## P0-11: Analytics Service — Missing @EnableCaching [COMPLETED]

**Issue:** `AnalyticsServiceApplication.java` has no `@EnableCaching` annotation. All `@Cacheable` annotations in `AdminAnalyticsService`, `VendorAnalyticsService`, and `CustomerAnalyticsService` are silently no-ops — every dashboard request fires 10+ HTTP calls.

- **File:** `analytics-service/.../AnalyticsServiceApplication.java`
  - Change: add `@EnableCaching` annotation to the class.

---

## P0-12: API Gateway — Missing Security Rules for Routes [COMPLETED]

**Issue:** `/reviews/**`, `/search/**`, `/analytics/admin/**`, `/analytics/vendor/**`, `/analytics/customer/**`, and `/admin/dashboard/**` have NO explicit security rules and fall through to `.anyExchange().authenticated()`. Public search and review browsing require auth (unintended). Analytics/dashboard have no role-based protection.

- **File:** `api-gateway/.../config/SecurityConfig.java`
  - Change: add `permitAll()` rules:
    - `.pathMatchers(HttpMethod.GET, "/reviews", "/reviews/**").permitAll()`
    - `.pathMatchers(HttpMethod.GET, "/search", "/search/**").permitAll()`
  - Change: add role-based rules:
    - `.pathMatchers("/analytics/admin/**", "/admin/dashboard/**").access(this::hasSuperAdminAccess)`
    - `.pathMatchers("/analytics/vendor/**").access(this::hasSuperAdminOrVendorAdminAccess)`
    - `.pathMatchers("/analytics/customer/**").authenticated()` (customer-specific — already auth'd, but now explicit)
  - Change: fix `/personalization/me/**` — currently `permitAll()` but contains user-specific data:
    - `.pathMatchers("/personalization/me/**").authenticated()`
    - Keep `/personalization/sessions/**`, `/personalization/events/**`, `/personalization/trending/**` as `permitAll()` for anonymous tracking.

---

## P0-13: Payment Service — No Ownership Check on getPayment/getPaymentByOrder [COMPLETED]

**Issue:** `PaymentController.getPayment()` and `getPaymentByOrder()` call `requireUserSub()` but do NOT verify that the authenticated user owns the payment. Any authenticated user can view ANY payment.

- **File:** `payment-service/.../service/PaymentService.java` (lines 321-332)
  - Change: add ownership check to `getPaymentById()` and `getPaymentByOrderId()` — verify `payment.getCustomerId().equals(customerId)`. Accept `customerId` as a parameter from the controller.
- **File:** `payment-service/.../controller/PaymentController.java` (lines 42-66)
  - Change: resolve customerId from `X-User-Sub` header and pass to service methods.

---

## P0-14: Vendor Service — Public Endpoints Expose Sensitive Data [COMPLETED]

**Issue:** Public `GET /vendors` and `GET /vendors/{idOrSlug}` return full `VendorResponse` including `commissionRate`, `verificationNotes`, `verificationDocumentUrl`, `contactEmail`, `contactPhone`, `deletionRequestReason`.

- **File:** `vendor-service/.../dto/` — create `PublicVendorResponse.java` record:
  - Include only: `id`, `name`, `slug`, `description`, `logoUrl`, `bannerUrl`, `primaryCategory`, `specializations`, `acceptingOrders`, `formattedAddress` (city/country only, no full address), `createdAt`.
  - Exclude: `commissionRate`, `verificationNotes`, `verificationDocumentUrl`, `contactEmail`, `contactPhone`, `contactPersonName`, `deletionRequestReason`, `taxId`, `payoutConfig`.
- **File:** `vendor-service/.../service/VendorServiceImpl.java`
  - Change: add a `toPublicResponse(Vendor)` mapping method.
- **File:** `vendor-service/.../controller/VendorController.java` (lines 25-44)
  - Change: `listActive()` and `getByIdOrSlug()` to return `PublicVendorResponse` instead of `VendorResponse`.

---

## P0-15: Vendor Service — Paginated listPublicActive Missing `acceptingOrders` Filter [COMPLETED]

**Issue:** `VendorServiceImpl.listPublicActive(String category, Pageable pageable)` does NOT filter by `isAcceptingOrders()`, unlike the non-paginated version. The paginated endpoint (used by `VendorController.listActive`) returns vendors that have stopped accepting orders.

- **File:** `vendor-service/.../service/VendorServiceImpl.java` (lines 147-154)
  - Change: add `acceptingOrders=true` to the repository query filter, or add post-query filtering via a Specification that includes `acceptingOrders = true`.
- **File:** `vendor-service/.../repo/VendorRepository.java`
  - Change: add paginated method: `Page<Vendor> findByDeletedFalseAndActiveTrueAndStatusAndAcceptingOrdersTrueOrderByNameAsc(VendorStatus status, Pageable pageable)` (and the category variant with `@Query`).

---

## P0-16: Promotion Service — CustomerPromotionController Uses Keycloak Sub as Customer ID [COMPLETED]

**Issue:** `CustomerPromotionController.couponUsage()` does `UUID.fromString(userSub)` to convert the Keycloak subject ID directly to a `customerId`. This is incorrect — keycloak sub and customer UUID are different.

- **File:** `promotion-service/.../controller/CustomerPromotionController.java` (line 33)
  - Change: add a `CustomerClient` dependency to the promotion-service (similar to cart-service's pattern).
  - Change: resolve customerId via `customerClient.getCustomerByKeycloakId(userSub).id()` instead of `UUID.fromString(userSub)`.
- **File:** `promotion-service/.../client/` — create `CustomerClient.java`:
  - Method: `CustomerSummary getCustomerByKeycloakId(String keycloakId)` with `@Retry` + `@CircuitBreaker`.

---

## P1-01: Vendor Service — Paginate Unbounded List Queries [COMPLETED]

**Issue:** `VendorRepository` has unbounded `List<Vendor>` methods that can return thousands of vendors.

- **File:** `vendor-service/.../repo/VendorRepository.java`
  - Remove the unbounded `List<>` overloads (lines 25-27, 36).
  - Keep: the paginated overloads already exist.
- **File:** `vendor-service/.../service/VendorServiceImpl.java`
  - Change: all callers of the removed List methods to use the paginated overloads.

---

## P1-02: Vendor Service — Paginate VendorUser Queries [COMPLETED]

**Issue:** `VendorUserRepository` has unbounded List queries for vendor users.

- **File:** `vendor-service/.../repo/VendorUserRepository.java`
  - Change: add `Pageable` overloads, return `Page<VendorUser>`.
- **File:** `vendor-service/.../service/VendorServiceImpl.java`
  - Change: all callers to use paginated overloads.

---

## P1-03: Product Service — Fix N+1 Variation Duplicate Check [COMPLETED]

**Issue:** `ProductServiceImpl` loads all variations for a parent product and iterates in-memory to check for duplicate variation signatures.

- **File:** `product-service/.../repo/ProductRepository.java`
  - Add: `boolean existsByParentProductIdAndDeletedFalseAndActiveTrueAndVariationSignature(UUID parentId, String variationSignature)`
- **File:** `product-service/.../service/ProductServiceImpl.java`
  - Change: replace the in-memory filter with the new repository method.

---

## P1-04: Product Service — Bound findTopByViews Query [COMPLETED]

**Issue:** `ProductRepository.findTopByViews()` has no LIMIT clause.

- **File:** `product-service/.../repo/ProductRepository.java`
  - Change: ensure the method accepts `Pageable` and returns `Page<>` so LIMIT is applied at SQL level.

---

## P1-05: Coupon Code Lookup — Remove upper() Function Call [COMPLETED]

**Issue:** `CouponCodeRepository.findByCodeWithPromotion()` uses `upper(c.code) = upper(:code)` which prevents index usage.

- **File:** `promotion-service/.../repo/CouponCodeRepository.java`
  - Change: normalize coupon codes to uppercase at write time and use exact match: `c.code = :code`.
- **File:** `promotion-service/.../service/CouponReservationService.java` and `CouponValidationService.java`
  - Change: normalize `couponCode` to uppercase before passing to repository calls.

---

## P1-06: Promotion Service — Add @Size Limit on Quote Lines List [COMPLETED]

**Issue:** `PromotionQuoteRequest.lines` has `@NotEmpty` but no max size.

- **File:** `promotion-service/.../dto/PromotionQuoteRequest.java`
  - Change: add `@Size(max = 200)` to the `lines` field.

---

## P1-07: Access Service — Paginate Session Listing [COMPLETED]

**Issue:** `ActiveSessionRepository.findByKeycloakIdOrderByLastActivityAtDesc()` returns unbounded List.

- **File:** `access-service/.../repo/ActiveSessionRepository.java`
  - Change: add paginated overload `Page<ActiveSession> findByKeycloakIdOrderByLastActivityAtDesc(String keycloakId, Pageable pageable)`
- **File:** `access-service/.../service/AccessServiceImpl.java`
  - Change: use paginated version. Default page size: 50.

---

## P1-08: Promotion Service — CouponCode Unbounded by Promotion in Admin [COMPLETED]

**Issue:** `CouponCodeRepository.findByPromotionIdOrderByCreatedAtDesc()` returns unbounded List.

- **File:** `promotion-service/.../repo/CouponCodeRepository.java`
  - Change: add paginated overload.
- **File:** Callers of this method — use paginated version.

---

## P1-09: API Gateway — Configure `spring.codec.max-in-memory-size` [COMPLETED]

**Issue:** Default `spring.codec.max-in-memory-size` is 256KB. The `RequestBodySizeLimitFilter` allows up to 2MB. The idempotency filter's `DataBufferUtils.join()` will throw `DataBufferLimitException` for request bodies between 256KB and 2MB.

- **File:** `api-gateway/src/main/resources/application.yaml`
  - Change: add `spring.codec.max-in-memory-size: ${GATEWAY_MAX_IN_MEMORY_SIZE:2MB}` to match `gateway.max-request-body-size`.

---

## P1-10: API Gateway — JSON Injection via Unescaped Path in Error Responses [COMPLETED]

**Issue:** Multiple filters construct JSON responses with string concatenation, and the request `path` value is NEVER escaped. An attacker can craft a path containing `"` to break JSON structure.

- **File:** `api-gateway/.../config/FallbackController.java` (line 21-26)
  - Change: apply `escapeJson()` to the path value in the JSON response body.
- **File:** `api-gateway/.../config/IpFilterConfig.java` (line 66-70)
  - Change: apply `escapeJson()` to the path value.
- **File:** `api-gateway/.../config/RequestBodySizeLimitFilter.java` (line 42-47)
  - Change: apply `escapeJson()` to both path and requestId values.
- **File:** `api-gateway/.../config/RateLimitEnforcementFilter.java` (lines 172-178, 211-217)
  - Change: apply `escapeJson()` to all interpolated values that aren't already escaped.
- **File:** `api-gateway/.../config/IdempotencyFilter.java` (lines 324-329)
  - Change: apply `escapeJson()` to the path value.

---

## P1-11: Customer Service — Keycloak Client Created Per Request [COMPLETED]

**Issue:** `KeycloakManagementService.newAdminClient()` creates a new `Keycloak` instance with a new `ResteasyClient` and connection pool per request. Under load, this creates massive connection churn.

- **File:** `customer-service/.../auth/KeycloakManagementService.java` (lines 189-203)
  - Change: create the `Keycloak` admin client once as a bean or field (initialized in constructor / `@PostConstruct`), and reuse it across all calls.
  - Add: proper cleanup in a `@PreDestroy` method to close the client.

---

## P1-12: API Gateway — Keycloak Admin Token Not Cached [COMPLETED]

**Issue:** `KeycloakManagementService.getAccessToken()` fetches a new token from Keycloak on EVERY call to `resendVerificationEmail`. Under load, this overwhelms the Keycloak server.

- **File:** `api-gateway/.../service/KeycloakManagementService.java` (lines 67-88)
  - Change: cache the access token in a field. Reuse the token until it expires (check expiry from the token response). Only refresh when expired or about to expire (e.g., 30s before expiry).

---

## P1-13: Product Service — Targeted Cache Eviction for productById [COMPLETED]

**Issue:** Every product mutation calls `bumpAllProductReadCaches()` which invalidates ALL cached product details. Editing Product A invalidates cached Product B. For list caches, broad eviction is unavoidable, but `productById` should use targeted eviction.

- **File:** `product-service/.../service/ProductCacheVersionService.java` (lines 39-43)
  - Change: split the cache version bumping. Keep broad eviction for list caches (`productsList`, `deletedProductsList`, `adminProductsList`). For `productById`, evict only the specific product's cache key instead of bumping the global version.
- **File:** `product-service/.../service/ProductServiceImpl.java`
  - Change: after product mutations, call a targeted eviction method that evicts only the mutated product's `productById` cache entry (by product ID or slug).

---

## P1-14: Payment Service — PayHereClient Missing Circuit Breaker [COMPLETED]

**Issue:** `PayHereClient` has NO `@CircuitBreaker` or `@Retry` annotations. If PayHere is down, refund and payment verification calls fail without circuit protection.

- **File:** `payment-service/.../service/PayHereClient.java`
  - Change: add `@CircuitBreaker(name = "payHereService", fallbackMethod = "...")` and `@Retry(name = "payHereService")` to the HTTP-calling methods (`submitRefund`, `getAccessToken`, etc.).
- **File:** `payment-service/src/main/resources/application.yaml`
  - Change: add circuit breaker configuration under `resilience4j.circuitbreaker.instances.payHereService` and `resilience4j.retry.instances.payHereService`.

---

## P1-15: Poster Service — Paginated Endpoints Missing isActive/Time-Window Filter [COMPLETED]

**Issue:** `PosterServiceImpl.listActiveByPlacement(PosterPlacement, Pageable)` and `listAllActive(Pageable)` (paginated versions) do NOT filter by `isActive` or `isActiveInWindow()`. The paginated public API returns inactive posters and posters outside their scheduled window.

- **File:** `poster-service/.../service/PosterServiceImpl.java` (lines 114-122)
  - Change: add `isActive=true` and `deleted=false` filters to the repository query for paginated overloads. For time-window filtering, either:
    - (a) Add repository-level conditions: `AND (p.startsAt IS NULL OR p.startsAt <= :now) AND (p.endsAt IS NULL OR p.endsAt >= :now)`, or
    - (b) Use a Specification that combines active + time-window checks.

---

## P1-16: Review Service — ReviewAnalyticsService totalReviews == activeReviews Bug [COMPLETED]

**Issue:** `ReviewAnalyticsService.getPlatformSummary()` passes `active` count for both `totalReviews` and `activeReviews`. `totalReviews` should include all reviews (active + inactive).

- **File:** `review-service/.../service/ReviewAnalyticsService.java` (line 36)
  - Change: query total reviews separately: `long total = reviewRepository.count()` (or a custom query counting all non-deleted reviews). Pass `total` as the first argument instead of `active`.

---

## P1-17: Admin Service — AdminPlatformStaffController Has No RBAC [COMPLETED]

**Issue:** All endpoints in `AdminPlatformStaffController` only verify `X-Internal-Auth`. There is no role-based access control — any authenticated internal caller (even vendor_admin or vendor_staff) can create, modify, or delete platform staff access records.

- **File:** `admin-service/.../controller/AdminPlatformStaffController.java` (lines 32-93)
  - Change: add `assertHasRole(userSub, userRoles, "super_admin")` check at the beginning of each endpoint method (listAll, create, update, delete, restore). Only `super_admin` should manage platform staff.
  - Add: `@RequestHeader("X-User-Sub") String userSub` and `@RequestHeader("X-User-Roles") String userRoles` parameters to each method.

---

## P1-18: Admin Service — Bulk Order Update Lacks Granular Permission Checks [COMPLETED]

**Issue:** `AdminOrderController` bulk status update and export endpoints use `assertHasRole(userSub, userRoles, "super_admin", "platform_staff")` but do NOT check the specific `PLATFORM_ORDERS_MANAGE` or `PLATFORM_ORDERS_READ` permission for platform_staff.

- **File:** `admin-service/.../controller/AdminOrderController.java` (lines 141, 173)
  - Change: for platform_staff role, additionally verify that the user has the `PLATFORM_ORDERS_MANAGE` permission (for bulk update) or `PLATFORM_ORDERS_READ` permission (for export) via `adminActorScopeService`.

---

## P1-19: Vendor Service — softDelete Self-Invocation Bypasses Transaction Design [COMPLETED]

**Issue:** `softDelete(UUID, String, String, String)` at line 224 is `@Transactional(REPEATABLE_READ)` and calls `confirmDelete()` at line 227 via `this.` (self-invocation). `confirmDelete` has `@Transactional(NOT_SUPPORTED)` which is IGNORED due to self-invocation. The two-phase commit design in `confirmDelete` (separate transactions for stop-orders, eligibility check, confirm-delete) is completely bypassed.

- **File:** `vendor-service/.../service/VendorServiceImpl.java` (lines 224-228)
  - Change: use the self-proxy pattern (like the existing `self` field at line 78-80). Call `self.confirmDelete(...)` instead of `this.confirmDelete(...)` so the `@Transactional(NOT_SUPPORTED)` on `confirmDelete` is respected by the Spring proxy.
- **File:** `vendor-service/.../service/VendorServiceImpl.java` (line 219-225)
  - Change: add `@CacheEvict(cacheNames = "vendorOperationalState", key = "#id")` to the `softDelete(UUID, String, String, String)` overload (currently only the `softDelete(UUID)` overload has it).

---

## P1-20: Customer Service — @CachePut Key References Non-Existent Method [COMPLETED]

**Issue:** `addLoyaltyPoints()` uses `@CachePut(key = "#result.keycloakId()")` but `CustomerResponse` does NOT have a `keycloakId()` method. This throws `EvaluationException` at runtime every time the cache put is attempted.

- **File:** `customer-service/.../service/CustomerServiceImpl.java` (line 492)
  - Change: either:
    - (a) Add `keycloakId` field to `CustomerResponse` record, or
    - (b) Change the `@CachePut` to use a different key. Since the method accepts `customerId`, look up the keycloakId from the entity before mapping to response, and use `key = "#keycloakId"` with a local variable, or
    - (c) Remove `@CachePut` from this method and instead use `@CacheEvict` on the relevant `customerByKeycloak` cache key.

---

## P2-01: Inter-Service Auth — HMAC Request Signing

**Issue:** All inter-service calls use a simple shared secret in `X-Internal-Auth` header.

### Approach
Replace with HMAC-SHA256 request signing. Each request includes timestamp + HMAC signature computed over `timestamp + HTTP method + path`.

- **All services** — each service has `security/InternalRequestVerifier.java`:
  - Change `verify()` to accept signature, timestamp, method, path. Verify HMAC and reject requests older than 60 seconds.
- **All services** — each HTTP client:
  - Change: compute and attach `X-Internal-Signature` and `X-Internal-Timestamp` headers.
- **All services** — each controller calling `verify()`:
  - Change: pass signature, timestamp, method, path to the updated verify method.

---

## P2-02: API Gateway — Add Content-Type Validation to Idempotency Filter

**Issue:** The idempotency filter buffers the entire request body for all mutating requests without checking Content-Type.

- **File:** `api-gateway/.../config/IdempotencyFilter.java`
  - Change: before buffering the body, check Content-Type. For multipart/binary, skip body hashing and use only the Idempotency-Key header.

---

## P2-03: API Gateway — Fix Request Body Size Limit Bypass

**Issue:** `RequestBodySizeLimitFilter` only checks `Content-Length` header, which can be omitted by clients using chunked transfer-encoding.

- **File:** `api-gateway/.../config/RequestBodySizeLimitFilter.java`
  - Change: add streaming body size enforcement via a wrapping `DataBuffer` that counts bytes and returns 413 if exceeded.

---

## P2-04: API Gateway — Require Email Verification for Admin Roles

**Issue:** `hasSuperAdminAccess()` and `hasSuperAdminOrVendorAdminAccess()` do not check `email_verified`.

- **File:** `api-gateway/.../config/SecurityConfig.java`
  - Change: add `isEmailVerified(jwt)` check to both methods.

---

## P2-05: API Gateway — Idempotency Filter Should Not Cache 4xx Errors

**Issue:** Transient 4xx errors get cached and replayed to retries with the same idempotency key.

- **File:** `api-gateway/.../config/IdempotencyFilter.java`
  - Change: do not cache 409 or 408 responses (or make non-cacheable statuses configurable).

---

## P2-06: Promotion Service — Coupon maxUses Race Condition

**Issue:** `maxUses` and `maxUsesPerCustomer` checks happen before pessimistic lock. Two concurrent reservations can both pass and exceed the limit.

- **File:** `promotion-service/.../service/CouponValidationService.java`
  - Change: move count checks into the locked section of `CouponReservationService.reserve()`.
- **File:** `promotion-service/.../service/CouponReservationService.java`
  - Change: after acquiring lock, re-verify maxUses/maxUsesPerCustomer counts.

---

## P2-07: Cart Service — Concurrent Cart Creation Race Condition

**Issue:** Two concurrent `addItem()` requests for a new user can both see "no cart" and try to insert.

- **File:** `cart-service/.../service/CartService.java` (addItem method)
  - Change: wrap "check + create" in try/catch for `DataIntegrityViolationException`. On catch, re-fetch the cart.

---

## P2-08: API Gateway — Validate and Sanitize X-Request-Id Header

**Issue:** `RequestIdFilter` accepts any user-provided `X-Request-Id` without validation.

- **File:** `api-gateway/.../config/RequestIdFilter.java`
  - Change: validate against `^[a-zA-Z0-9\-]{1,64}$`. If invalid, generate new UUID.

---

## P2-09: API Gateway — Expose Idempotency Status Headers in CORS

**Issue:** CORS exposed-headers list doesn't include idempotency-related response headers.

- **File:** `api-gateway/src/main/resources/application.yaml` (CORS exposed-headers)
  - Change: add `X-Idempotency-Status` and `X-Idempotency-Key`.

---

## P2-10: API Gateway — Configure Redis Connection Pool

**Issue:** Only `host`, `port`, and `timeout` are configured for Redis. No connection pool settings. Under high load, the default single-connection behavior is a bottleneck.

- **File:** `api-gateway/src/main/resources/application.yaml`
  - Change: add Lettuce pool configuration:
    ```yaml
    spring.data.redis.lettuce.pool:
      max-active: ${REDIS_POOL_MAX_ACTIVE:16}
      max-idle: ${REDIS_POOL_MAX_IDLE:8}
      min-idle: ${REDIS_POOL_MIN_IDLE:2}
      max-wait: ${REDIS_POOL_MAX_WAIT:2000ms}
    ```

---

## P2-11: Personalization Service — Add Internal Auth to Events Endpoint

**Issue:** `/personalization/events` POST endpoint has no authentication. Anyone reaching the service can record arbitrary events for any user/session, enabling recommendation data poisoning.

- **File:** `personalization-service/.../controller/EventController.java` (lines 20-32)
  - Change: add `@RequestHeader("X-Internal-Auth") String internalAuth` parameter and call `internalRequestVerifier.verify(internalAuth)` at the start of the method.

---

## P2-12: Wishlist Service — Default Collection Race Condition (No Unique Constraint)

**Issue:** `getOrCreateDefaultCollection()` catches `DataIntegrityViolationException` for race conditions, but there is no unique constraint on `(keycloak_id, is_default)`. Two concurrent requests can both create a default collection.

- **File:** `wishlist-service/.../entity/WishlistCollection.java` (line 23-32)
  - Change: add a unique constraint:
    ```java
    @Table(name = "wishlist_collections", uniqueConstraints = {
        @UniqueConstraint(name = "uk_collections_keycloak_default", columnNames = {"keycloak_id", "is_default"})
    })
    ```
  - Note: This works for preventing multiple `is_default=true` AND multiple `is_default=false` rows with the same keycloak_id. A partial unique index (`WHERE is_default = true`) would be more precise but requires native DDL. The current approach is acceptable.

---

## P2-13: Order Service — Self-Invocation in updateStatus → updateVendorOrderStatus

**Issue:** `updateStatus()` calls `updateVendorOrderStatus()` within the same class. Since this is self-invocation, the `@Transactional` annotation on `updateVendorOrderStatus()` is bypassed.

- **File:** `order-service/.../service/OrderService.java` (line 268)
  - Change: since both methods need to be in the same transaction anyway (they share the pessimistic lock), the behavior is actually correct. Remove the misleading `@Transactional` annotation from `updateVendorOrderStatus()` to avoid confusion, and add a comment documenting that it runs within the caller's transaction.

---

## P2-14: Payment Service — AdminBankAccountController.setPrimary() No Locking

**Issue:** `setPrimary()` reads all bank accounts, unsets their primary flag, then sets the new one. No pessimistic lock — concurrent calls can leave multiple accounts primary.

- **File:** `payment-service/.../controller/AdminBankAccountController.java` (lines 107-123)
  - Change: wrap the read-unset-set operation in a single `@Transactional` method (or move to a service class) and use `@Lock(PESSIMISTIC_WRITE)` on the query that reads the vendor's bank accounts. Alternatively, use a single UPDATE query: `UPDATE BankAccount SET isPrimary = (id = :newPrimaryId) WHERE vendorId = :vendorId`.

---

## P2-15: N+1 — RefundService.toResponse() Lazy-Loads Payment

**Issue:** `RefundRequest.payment` is `FetchType.LAZY`. `toResponse()` accesses `r.getPayment().getId()`, triggering a lazy load per refund in paginated list queries.

- **File:** `payment-service/.../repo/RefundRequestRepository.java`
  - Change: add `@EntityGraph(attributePaths = "payment")` to the list queries used by `listRefundsForCustomer`, `listRefundsForVendor`, and `listAllRefunds`. Or use `JOIN FETCH` in `@Query`.

---

## P2-16: N+1 — Vendor listAccessibleVendorMembershipsByKeycloakUser

**Issue:** Loads `VendorUser` entities (with `vendor` as `FetchType.LAZY`) then accesses `user.getVendor().getId()`, `.getSlug()`, `.getName()` for each — N+1 queries.

- **File:** `vendor-service/.../repo/VendorUserRepository.java` (line 14)
  - Change: add `JOIN FETCH vu.vendor` to the query.

---

## P2-17: N+1 — Category Attributes in CategoryServiceImpl.toResponse()

**Issue:** `toResponse()` queries `categoryAttributeRepository.findByCategoryIdOrderByDisplayOrderAsc()` for EACH category in a list. 21 queries for 20 categories.

- **File:** `product-service/.../service/CategoryServiceImpl.java` (lines 340-344)
  - Change: for list operations, batch-fetch all category attributes in a single query: `findByCategoryIdInOrderByDisplayOrderAsc(Set<UUID> categoryIds)`. Build a `Map<UUID, List<CategoryAttribute>>` and use it in the mapping loop.

---

## P2-18: N+1 — Wishlist getCollections → toCollectionResponse

**Issue:** `getCollections()` fetches all collections, then `toCollectionResponse()` calls `wishlistItemRepository.findByCollectionOrderByCreatedAtDesc(collection)` per collection. 1 + N queries.

- **File:** `wishlist-service/.../service/WishlistService.java` (lines 151-158, 393-411)
  - Change: batch-fetch items for all collections in one query: `findByCollectionInOrderByCreatedAtDesc(List<WishlistCollection> collections)`. Group results into a `Map<UUID, List<WishlistItem>>` and use it in the mapping.

---

## P2-19: N+1 — Poster toResponse() Variant Query

**Issue:** `PosterServiceImpl.toResponse()` issues `posterVariantRepository.findByPosterIdOrderByCreatedAtAsc()` per poster.

- **File:** `poster-service/.../service/PosterServiceImpl.java` (line 309)
  - Change: for list operations, batch-fetch variants: `findByPosterIdInOrderByCreatedAtAsc(List<UUID> posterIds)`. Group into `Map<UUID, List<PosterVariant>>`.

---

## P2-20: Access Service — deactivateExpiredAccess Loads All Expired Records Without Batching

**Issue:** `findByActiveTrueAndDeletedFalseAndAccessExpiresAtBefore(now)` loads ALL expired records into memory at once.

- **File:** `access-service/.../service/AccessServiceImpl.java` (lines 989-1021)
  - Change: process in batches using `PageRequest.of(0, 100)` in a loop. Process each batch, then fetch next page (using page 0 each time since processed records change status).

---

## P2-21: Access Service — N+1 on @ElementCollection(EAGER) for Permissions

**Issue:** `PlatformStaffAccess.permissions` and `VendorStaffAccess.permissions` use `@ElementCollection(fetch = FetchType.EAGER)`, causing a separate SELECT per entity for the collection table.

- **File:** `access-service/.../entity/PlatformStaffAccess.java` (line 64)
  - Change: add `@BatchSize(size = 50)` to the `permissions` collection.
- **File:** `access-service/.../entity/VendorStaffAccess.java` (line 64)
  - Change: add `@BatchSize(size = 50)` to the `permissions` collection.

---

## P2-22: CROSS-SERVICE — Handle OptimisticLockingFailureException

**Issue:** Multiple services use `@Version` but none handle `OptimisticLockingFailureException`. Concurrent modification conflicts return 500 instead of 409 CONFLICT.

- **Files:** `GlobalExceptionHandler.java` in: product-service, inventory-service, payment-service, order-service, vendor-service, review-service, poster-service, access-service
  - Change: add handler in each:
    ```java
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(OptimisticLockingFailureException ex) {
        return buildResponse(HttpStatus.CONFLICT, "Resource was modified by another request. Please retry.");
    }
    ```

---

## P2-23: CROSS-SERVICE — Refactor RestClient to Build Once in Constructor

**Issue:** Every inter-service HTTP client calls `lbRestClientBuilder.build()` on every method invocation, creating a new `RestClient` instance per request.

- **Files:** All client classes across all services (ProductClient, OrderClient, CustomerClient, VendorClient, AccessClient, CartClient, PromotionClient, InventoryClient, WishlistClient, etc.)
  - Change: build `RestClient` once in the constructor and store as a `private final RestClient restClient` field. Replace all `lbRestClientBuilder.build()` calls within methods with the stored field.

---

## P2-24: CROSS-SERVICE — Remove Unnecessary WebFlux/Reactive Dependencies

**Issue:** vendor-service, poster-service, and access-service include both `spring-boot-starter-webmvc` AND `spring-boot-starter-webflux` plus `spring-boot-starter-data-redis-reactive`, even though they use synchronous servlet-based code.

- **File:** `vendor-service/pom.xml`
  - Change: remove `spring-boot-starter-webflux` and `spring-boot-starter-data-redis-reactive`. Keep `spring-boot-starter-web` and `spring-boot-starter-data-redis`.
- **File:** `poster-service/pom.xml`
  - Change: same — remove webflux and reactive Redis starters. Also remove `spring-cloud-starter-circuitbreaker-reactor-resilience4j` (unused) and keep `spring-cloud-starter-circuitbreaker-resilience4j` if needed.
- **File:** `access-service/pom.xml`
  - Change: same — remove webflux and reactive Redis starters.

---

## P2-25: Poster Service — Rate Limit Click/Impression Endpoints

**Issue:** `POST /posters/{id}/click` and `POST /posters/{id}/impression` are unauthenticated with no rate limiting. An attacker can inflate analytics.

- **File:** `poster-service/.../controller/PosterController.java` (lines 74-93)
  - Change: add IP-based rate limiting. Create a `PosterClickRateLimiter` (similar to `ImageUploadRateLimiter` in product-service) that uses Redis to track clicks per IP per poster per time window (e.g., max 5 clicks per poster per IP per minute).
  - Alternatively: add rate limiting at the gateway level for these specific paths in `RateLimitEnforcementFilter.resolvePolicy()`.

---

## P2-26: Admin Service — Missing MethodArgumentNotValidException Handler

**Issue:** admin-service `GlobalExceptionHandler` does not handle `MethodArgumentNotValidException`. `@Valid` failures return Spring's default format instead of the consistent `{timestamp, status, error, message}` format.

- **File:** `admin-service/.../exception/GlobalExceptionHandler.java`
  - Change: add handler:
    ```java
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .collect(Collectors.joining(", "));
        return buildResponse(HttpStatus.BAD_REQUEST, message);
    }
    ```

---

## P2-27: Admin Service — Idempotency Gaps

**Issue:** `AdminMutationIdempotencyFilter` does not cover: (a) `PATCH /admin/orders/{id}/note`, (b) `POST /admin/orders/bulk-status-update`.

- **File:** `admin-service/.../config/AdminMutationIdempotencyFilter.java` (lines 39-41)
  - Change: add PATCH pattern `^/admin/orders/[^/]+/note$` and POST pattern `^/admin/orders/bulk-status-update$` to `isProtectedMutationPath`.

---

## P2-28: Customer Service — No Circuit Breaker on Keycloak Calls

**Issue:** No Resilience4j protection on Keycloak admin API calls. If Keycloak is down, every registration or profile update hangs for 10s before failing.

- **File:** `customer-service/pom.xml`
  - Change: add `spring-cloud-starter-circuitbreaker-resilience4j` dependency.
- **File:** `customer-service/.../auth/KeycloakManagementService.java`
  - Change: wrap Keycloak calls with `@CircuitBreaker(name = "keycloak")` and `@Retry(name = "keycloak")`.
- **File:** `customer-service/src/main/resources/application.yaml`
  - Change: add `resilience4j.circuitbreaker.instances.keycloak` and `resilience4j.retry.instances.keycloak` configuration.

---

## P2-29: Analytics Service — CompletableFuture Uses ForkJoinPool.commonPool

**Issue:** All `CompletableFuture.supplyAsync()` calls use the default `ForkJoinPool.commonPool()`, shared with all framework components. Under load, saturation causes backpressure.

- **File:** `analytics-service/.../config/` — create `AsyncConfig.java`:
  - Define a dedicated `ExecutorService` bean (e.g., `Executors.newFixedThreadPool(20)` or virtual threads).
- **File:** `analytics-service/.../service/AdminAnalyticsService.java`, `VendorAnalyticsService.java`, `CustomerAnalyticsService.java`
  - Change: inject the custom `ExecutorService` and pass as second argument: `CompletableFuture.supplyAsync(() -> ..., analyticsExecutor)`.

---

## P2-30: Vendor Service — VendorLifecycleActionRequest Missing @Valid

**Issue:** `AdminVendorController` endpoints for requestDelete, approveDelete, etc., accept `@RequestBody(required = false) VendorLifecycleActionRequest` without `@Valid`. The `@Size(max = 500)` on `reason` is never enforced.

- **File:** `vendor-service/.../controller/AdminVendorController.java` (lines 129, 140, 152, 164, 177)
  - Change: add `@Valid` before `@RequestBody` on each of these method parameters.

---

## P2-31: Search Service — Full Reindex Does Not Remove Stale Products

**Issue:** `ProductIndexService.fullReindex()` pages through products and saves them, but never removes products deleted from the source that still exist in the ES index.

- **File:** `search-service/.../service/ProductIndexService.java` (lines 39-73)
  - Change: implement alias-swap reindexing:
    1. Create a new index (e.g., `products_v2`) with the same settings.
    2. Index all products into the new index.
    3. Atomically swap the alias `products` from old to new index.
    4. Delete the old index.
  - Alternatively: after reindex completes, delete all documents with `indexedAt` older than the reindex start time.

---

## P2-32: Search Service — fullReindex Reports COMPLETED on Partial Failure

**Issue:** If an exception occurs during reindex, the loop breaks, but the method still returns `"COMPLETED"` status.

- **File:** `search-service/.../service/ProductIndexService.java` (lines 63-72)
  - Change: track whether any page failed. If so, return status `"PARTIAL_FAILURE"` or `"FAILED"` instead of `"COMPLETED"`.

---

## P2-33: Review Service — VendorClient Circuit Breaker Config Missing

**Issue:** `VendorClient` references circuit breaker/retry named `vendorService` but `application.yaml` only configures `orderService` and `customerService`. Falls back to Resilience4j defaults.

- **File:** `review-service/src/main/resources/application.yaml`
  - Change: add `resilience4j.circuitbreaker.instances.vendorService` and `resilience4j.retry.instances.vendorService` configuration blocks.

---

## P2-34: Personalization Service — Unbounded `limit` Parameters

**Issue:** `/personalization/recommendations`, `/personalization/trending`, etc., accept `limit` parameter with no upper bound. A caller can request `limit=999999`.

- **File:** `personalization-service/.../controller/PersonalizationController.java` (lines 28, 44, 54)
  - Change: add `@Max(100)` validation to `limit` parameters.
- **File:** `personalization-service/.../controller/ProductRecommendationController.java` (lines 23, 31)
  - Change: add `@Max(100)` validation to `limit` parameters.

---

## P2-35: Cart Service — Missing Index on lastActivityAt

**Issue:** `CartRepository.deleteExpiredCarts()` queries `WHERE c.lastActivityAt < :cutoff` but there is no index on `lastActivityAt`.

- **File:** `cart-service/.../entity/Cart.java` (line 65-67)
  - Change: add `@Index(name = "idx_carts_last_activity", columnList = "last_activity_at")` to the `@Table` indexes.

---

## P2-36: Cart Service — Missing @Max on Item Quantity DTOs

**Issue:** `AddCartItemRequest.quantity` and `UpdateCartItemRequest.quantity` only have `@Min(1)`. The 1000 max is only enforced in the service layer.

- **File:** `cart-service/.../dto/AddCartItemRequest.java` (line 10)
  - Change: add `@Max(1000)` next to `@Min(1)`.
- **File:** `cart-service/.../dto/UpdateCartItemRequest.java` (line 6)
  - Change: add `@Max(1000)`.

---

## P3-01: Payment Service — Audit Trail Returns Unbounded List [COMPLETED]


**Issue:** `PaymentService.getAuditTrail()` returns `List<PaymentAuditResponse>` without pagination.

- **File:** `payment-service/.../service/PaymentService.java` (line 372-378)
  - Change: add Pageable parameter, return `Page<PaymentAuditResponse>`.
- **File:** `payment-service/.../repo/PaymentAuditRepository.java`
  - Change: add paginated overload.

---

## P3-02: Cart Service — Analytics Aggregation Queries Missing Time Bounds [COMPLETED]

**Issue:** `CartItemRepository` has `countActiveCartItems()` and `avgCartItemValue()` that scan the entire table.

- **File:** `cart-service/.../repo/CartItemRepository.java`
  - Change: add `since` parameter to scope aggregations to a time window.

---

## P3-03: Search Service — Add Query Timeout to Elasticsearch Queries [COMPLETED]

**Issue:** Hardcoded 10-second timeout with no configurability.

- **File:** `search-service/.../service/ProductSearchService.java`
  - Change: make timeout configurable via `@Value("${search.query.timeout-seconds:10}")`.

---

## P3-04: Product Service — Vendor State Resolution Not Cached During Catalog Rebuild [COMPLETED]

**Issue:** `ProductCatalogReadModelProjector` calls vendor service for every product during rebuild.

- **File:** `product-service/.../service/ProductCatalogReadModelProjector.java`
  - Change: batch-fetch vendor states at the page level into a local `Map<UUID, VendorOperationalStateResponse>`.

---

## P3-05: Vendor Service — Analytics Leaderboard/Customer Analytics Unbounded Parameters [COMPLETED]

**Issue:** `InternalVendorAnalyticsController.topVendors` limit has no max. `InternalCustomerAnalyticsController.months` has no max.

- **File:** `vendor-service/.../controller/InternalVendorAnalyticsController.java` (line 31)
  - Change: add `@Max(100)` to `limit` parameter.
- **File:** `customer-service/.../controller/InternalCustomerAnalyticsController.java` (line 30)
  - Change: add `@Max(120)` to `months` parameter.

---

## P3-06: Vendor Service — Vendor Lifecycle Audit Unpaginated [COMPLETED]

**Issue:** `AdminVendorController.listLifecycleAudit` returns `List<VendorLifecycleAuditResponse>` without pagination.

- **File:** `vendor-service/.../controller/AdminVendorController.java` (lines 63-70)
  - Change: add `Pageable` parameter, return `Page<>`.
- **File:** `vendor-service/.../service/VendorServiceImpl.java`
  - Change: update `listLifecycleAudit` to accept `Pageable` and pass to repository.

---

## P3-07: Promotion Service — Unused promotionQuotePreview Cache Config [COMPLETED]

**Issue:** `CacheConfig` configures `promotionQuotePreview` cache (line 63) but no method uses `@Cacheable(cacheNames = "promotionQuotePreview")`.

- **File:** `promotion-service/.../config/CacheConfig.java` (line 63)
  - Change: remove the unused `promotionQuotePreview` cache configuration.

---

## P3-08: Promotion Service — Dead Code: isApprovalEligible() [COMPLETED]

**Issue:** `PromotionQuoteService.isApprovalEligible()` is `private` and never called.

- **File:** `promotion-service/.../service/PromotionQuoteService.java` (line 613)
  - Change: remove the dead method.

---

## P3-09: Review Service — Unused adminReviewsList Cache Config [COMPLETED]

**Issue:** `CacheConfig` defines `adminReviewsList` cache (line 62) but `adminList()` has no `@Cacheable` annotation.

- **File:** `review-service/.../config/CacheConfig.java` (line 62)
  - Change: remove the unused `adminReviewsList` cache configuration.

---

## P3-10: Review Service — Dead Imports in ReviewServiceImpl [COMPLETED]

**Issue:** `Autowired` and `Lazy` are imported but never used.

- **File:** `review-service/.../service/ReviewServiceImpl.java` (lines 13-14 area)
  - Change: remove unused `import org.springframework.beans.factory.annotation.Autowired` and `import org.springframework.context.annotation.Lazy`.

---

## P3-11: Wishlist Service — Unused Imports [COMPLETED]

**Issue:** `TransactionSynchronization` and `TransactionSynchronizationManager` are imported but never used.

- **File:** `wishlist-service/.../service/WishlistService.java` (lines 31-32)
  - Change: remove unused imports.

---

## P3-12: Promotion Service — Missing spring-boot-starter-actuator [COMPLETED]

**Issue:** Cart-service and wishlist-service include `spring-boot-starter-actuator` but promotion-service does not. Health checks and metrics are unavailable.

- **File:** `promotion-service/pom.xml`
  - Change: add `spring-boot-starter-actuator` dependency.

---

## P3-13: CROSS-SERVICE — Spring Boot/Spring Cloud Version Alignment [COMPLETED]

**Issue:** Services use different Spring Boot versions (4.0.2 vs 4.0.3) and Spring Cloud versions (2025.1.0 vs 2025.1.1). This can cause subtle incompatibilities.

- **Files:** All `pom.xml` files across all 18 services
  - Change: align all services to the same Spring Boot version (4.0.3) and Spring Cloud version (2025.1.1).

---

## P3-14: Poster Service — Poster Image Upload Missing Per-File Size and Content Validation [COMPLETED]

**Issue:** Poster-service `uploadImages` has no per-file size check or image content validation (unlike review-service which validates size and dimensions).

- **File:** `poster-service/.../storage/PosterImageStorageServiceImpl.java` (lines 51-98)
  - Change: add per-file size validation (e.g., max 5MB per poster image). Read the image via `ImageIO.read()` to verify it is a valid image before uploading to S3.

---

## P3-15: Review Service — No maxPageSize Limiter [COMPLETED]

**Issue:** review-service has no `PaginationConfig` to enforce a maximum page size. Clients can request `?size=10000`.

- **File:** `review-service/.../config/` — create `PaginationConfig.java`:
  - Same pattern as other services: `@Override public void addArgumentResolvers(...)` with `resolver.setMaxPageSize(100)`.

---

## P3-16: Poster Service — Caffeine (Local) Cache Stale Across Instances [COMPLETED]

**Issue:** Poster-service uses Caffeine (process-local) cache with 60s TTL. In a multi-instance deployment, updates on one instance are invisible to others for up to 60s.

- **File:** `poster-service/.../config/CacheConfig.java`
  - Change: consider migrating to Redis-backed cache (like review-service does) for consistency across instances. Alternatively, reduce TTL to 15s and document the acceptable staleness window.

---

---

## Summary Matrix

| Task | Service | Priority | Type |
|------|---------|----------|------|
| P0-01 | api-gateway | Critical | Security |
| P0-02 | api-gateway | Critical | Security |
| P0-03 | promotion-service | Critical | Performance |
| P0-04 | promotion-service | Critical | Bug |
| P0-05 | cart-service | Critical | Performance |
| P0-06 | order-service | Critical | Performance + Security |
| P0-07 | order-service | Critical | Reliability |
| P0-08 | access-service | Critical | Logic Bug |
| P0-09 | review-service | Critical | Compilation Error |
| P0-10 | review-service | Critical | Missing Annotation |
| P0-11 | analytics-service | Critical | Missing Annotation |
| P0-12 | api-gateway | Critical | Security (RBAC) |
| P0-13 | payment-service | Critical | Security |
| P0-14 | vendor-service | Critical | Data Leak |
| P0-15 | vendor-service | Critical | Logic Bug |
| P0-16 | promotion-service | Critical | Bug |
| P1-01 | vendor-service | High | Performance |
| P1-02 | vendor-service | High | Performance |
| P1-03 | product-service | High | Performance |
| P1-04 | product-service | High | Performance |
| P1-05 | promotion-service | High | Performance |
| P1-06 | promotion-service | High | Validation |
| P1-07 | access-service | High | Performance |
| P1-08 | promotion-service | High | Performance |
| P1-09 | api-gateway | High | Bug |
| P1-10 | api-gateway | High | Security |
| P1-11 | customer-service | High | Performance |
| P1-12 | api-gateway | High | Performance |
| P1-13 | product-service | High | Caching |
| P1-14 | payment-service | High | Reliability |
| P1-15 | poster-service | High | Logic Bug |
| P1-16 | review-service | High | Logic Bug |
| P1-17 | admin-service | High | Security (RBAC) |
| P1-18 | admin-service | High | Security (RBAC) |
| P1-19 | vendor-service | High | Transaction Bug |
| P1-20 | customer-service | High | Runtime Bug |
| P2-01 | all services | Medium | Security |
| P2-02 | api-gateway | Medium | Performance |
| P2-03 | api-gateway | Medium | Security |
| P2-04 | api-gateway | Medium | Security |
| P2-05 | api-gateway | Medium | Correctness |
| P2-06 | promotion-service | Medium | Concurrency |
| P2-07 | cart-service | Medium | Concurrency |
| P2-08 | api-gateway | Medium | Security |
| P2-09 | api-gateway | Medium | CORS |
| P2-10 | api-gateway | Medium | Performance |
| P2-11 | personalization-service | Medium | Security |
| P2-12 | wishlist-service | Medium | Concurrency |
| P2-13 | order-service | Medium | Code Clarity |
| P2-14 | payment-service | Medium | Concurrency |
| P2-15 | payment-service | Medium | N+1 |
| P2-16 | vendor-service | Medium | N+1 |
| P2-17 | product-service | Medium | N+1 |
| P2-18 | wishlist-service | Medium | N+1 |
| P2-19 | poster-service | Medium | N+1 |
| P2-20 | access-service | Medium | Performance |
| P2-21 | access-service | Medium | N+1 |
| P2-22 | cross-service | Medium | Exception Handling |
| P2-23 | cross-service | Medium | Performance |
| P2-24 | cross-service | Medium | Dependency Cleanup |
| P2-25 | poster-service | Medium | Security |
| P2-26 | admin-service | Medium | Exception Handling |
| P2-27 | admin-service | Medium | Idempotency |
| P2-28 | customer-service | Medium | Reliability |
| P2-29 | analytics-service | Medium | Performance |
| P2-30 | vendor-service | Medium | Validation |
| P2-31 | search-service | Medium | Data Integrity |
| P2-32 | search-service | Medium | Correctness |
| P2-33 | review-service | Medium | Configuration |
| P2-34 | personalization-service | Medium | Validation |
| P2-35 | cart-service | Medium | Indexing |
| P2-36 | cart-service | Medium | Validation |
| P3-01 | payment-service | Low | Defensive |
| P3-02 | cart-service | Low | Performance |
| P3-03 | search-service | Low | Config |
| P3-04 | product-service | Low | Performance |
| P3-05 | vendor/customer | Low | Validation |
| P3-06 | vendor-service | Low | Pagination |
| P3-07 | promotion-service | Low | Dead Config |
| P3-08 | promotion-service | Low | Dead Code |
| P3-09 | review-service | Low | Dead Config |
| P3-10 | review-service | Low | Dead Imports |
| P3-11 | wishlist-service | Low | Dead Imports |
| P3-12 | promotion-service | Low | Missing Dependency |
| P3-13 | cross-service | Low | Version Alignment |
| P3-14 | poster-service | Low | Validation |
| P3-15 | review-service | Low | Pagination |
| P3-16 | poster-service | Low | Caching |
