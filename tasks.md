# Microservice Audit - Tasks

> All items implemented.

## 1. SELF-INVOCATION BUGS (CRITICAL - Proxy Bypass) - DONE

### 1.1 vendor-service: `getOperationalStates()` bypasses `@Cacheable` on `getOperationalState()` ✅
- **File:** `Services/vendor-service/src/main/java/com/rumal/vendor_service/service/VendorServiceImpl.java`
- Added `@Lazy @Autowired private VendorServiceImpl self;` and changed `this::getOperationalState` to `self::getOperationalState`

### 1.2 vendor-service: `stopReceivingOrders(UUID)` bypasses `@CacheEvict` ✅
- **File:** `Services/vendor-service/src/main/java/com/rumal/vendor_service/service/VendorServiceImpl.java`
- Added `@CacheEvict(cacheNames = "vendorOperationalState", key = "#id")` to single-arg method

### 1.3 vendor-service: `resumeReceivingOrders(UUID)` bypasses `@CacheEvict` ✅
- Same fix as 1.2

### 1.4 vendor-service: `restore(UUID)` bypasses `@CacheEvict` ✅
- Same fix as 1.2

### 1.5 vendor-service: `softDelete(UUID)` chain bypasses `@CacheEvict` ✅
- Added `@CacheEvict` to top-level `softDelete(UUID)` method

### 1.6 product-service: `getByIdOrSlug()` bypasses `@Cacheable` ✅
- **File:** `Services/product-service/src/main/java/com/rumal/product_service/service/ProductServiceImpl.java`
- Added `@Lazy @Autowired private ProductServiceImpl self;` and changed `this.getById()` / `this.getBySlug()` to `self.getById()` / `self.getBySlug()`

---

## 2. SECURITY - DONE

### 2.1 customer-service: 4 endpoints missing X-Internal-Auth verification ✅
- **File:** `Services/customer-service/src/main/java/com/rumal/customer_service/controller/CustomerController.java`
- Added `internalRequestVerifier.verify(internalAuth)` to `POST /customers`, `GET /customers/{id}`, `GET /customers/by-email`, `GET /customers/{customerId}/addresses/{addressId}`

### 2.2 order-service: 4 endpoints missing X-Internal-Auth verification ✅
- **File:** `Services/order-service/src/main/java/com/rumal/order_service/controller/OrderController.java`
- Added `internalRequestVerifier.verify(internalAuth)` to `POST /orders`, `GET /orders/{id}`, `GET /orders`, `GET /orders/{id}/details`

### 2.3 InternalRequestVerifier timing attack fix (all 10 services) ✅
- Replaced `sharedSecret.equals(headerValue)` with `MessageDigest.isEqual()` in all 10 services

### 2.4 admin-service: Map<String,Object> bodies bypass all validation
- **Status:** Deferred -- BFF pass-through pattern requires significant refactoring to typed DTOs

---

## 3. CACHING - DONE

### 3.1 customer-service: Missing cache eviction on `addLoyaltyPoints()` ✅
- Added `@CachePut(cacheNames = "customerByKeycloak", key = "#result.keycloakId()")`

### 3.2 admin-service: Mixed Jackson version (Jackson 2 vs Jackson 3) ✅
- Migrated to `GenericJacksonJsonRedisSerializer` (Jackson 3) with proper type validator

### 3.3 Missing Redis key prefix isolation (5 services) ✅
- Added `computePrefixWith()` with service-specific prefixes: `cs:v1::`, `os:v1::`, `cart:v1::`, `wl:v1::`, `admin:v1::`

### 3.4 Missing CacheErrorHandler (5 services) ✅
- Added `CacheErrorHandler` to customer-service, order-service, cart-service, wishlist-service, admin-service

### 3.5 promotionAdminList cache evicted but never populated ✅
- Added `@Cacheable(cacheNames = "promotionAdminList")` to `list()` method

### 3.6 Flash sales endpoint not cached ✅
- Added `@Cacheable(cacheNames = "publicPromotionList", key = "'flashSales'")`

### 3.7 Missing explicit TTL for public promotion caches ✅
- Added `publicPromotionList` (90s) and `publicPromotionById` (120s) TTL entries

---

## 4. IDEMPOTENCY - DONE

### 4.1 wishlist-service: NO idempotency filter ✅
- Created `WishlistMutationIdempotencyFilter` + `AbstractRedisServletIdempotencyFilter`

### 4.2 product-service: NO idempotency filter ✅
- Created `ProductMutationIdempotencyFilter` + `AbstractRedisServletIdempotencyFilter`

### 4.3 poster-service: NO idempotency filter ✅
- Created `PosterMutationIdempotencyFilter` + `AbstractRedisServletIdempotencyFilter`

### 4.4 No idempotency key format/length validation ✅
- Added `^[a-zA-Z0-9\-_]{1,128}$` validation to gateway IdempotencyFilter + all 9 servlet filter copies

### 4.5 vendor-service missing Redis host configuration ✅
- Added `spring.data.redis` config to application.yaml

### 4.6 vendor-service: PUT paths not covered by idempotency ✅
- Added `PUT /vendors/me` and `PUT /vendors/me/payout-config` to filter

---

## 5. RATE LIMITING - DONE

### 5.1 publicPromotionsRateLimiter dead code ✅
- Wired `publicPromotionsRateLimiter` in `resolvePolicy()` for `/promotions/me/**` and `/promotions/**`

---

## 6. CIRCUIT BREAKER / RESILIENCE - DONE

### 6.1 wishlist-service CartClient retries POST without idempotency key ✅
- Added `Idempotency-Key` header with `UUID.randomUUID()` to `addItemToCart()` call

### 6.2 Gateway WebClient has no timeout configuration ✅
- Added reactor-netty HttpClient with connect timeout (3s) and response timeout (5s)

---

## 7. @TRANSACTIONAL ISSUES - DONE

### 7.1 order-service: HTTP call inside transaction ✅
- Moved `maybeReleaseCouponReservationForFinalStatus()` to `afterCommit` callback in `cancelMyOrder()`, `updateStatus()`, `updateVendorOrderStatus()`

### 7.2 wishlist-service: HTTP call inside transaction in `moveItemToCart()` ✅
- Reordered: delete item in transaction, call cart in `afterCommit` callback

### 7.3 promotion-service: CouponCodeAdminService bare `@Transactional` ✅
- Added class-level `@Transactional(readOnly=true, isolation=READ_COMMITTED, timeout=10)`, proper overrides on writes

### 7.4 cart-service: CartExpiryScheduler no error handling ✅
- Added try-catch and explicit `@Transactional(isolation=READ_COMMITTED, timeout=30)`

### 7.5 promotion-service: CouponReservationCleanupJob no error handling ✅
- Added try-catch and explicit `@Transactional(isolation=READ_COMMITTED, timeout=30)`

---

## 8. VALIDATION - DONE

### 8.1 customer-service: RegisterIdentityCustomerRequest zero validation ✅
- Added `@Size(max=120)` on name, `@Valid` in controller

### 8.2 order-service: No size limit on order items list ✅
- Added `@Size(max=100)` on items in `CreateOrderRequest` and `CreateMyOrderRequest`

### 8.3 promotion-service: PromotionQuoteRequest missing @Size ✅
- Added `@Size` to couponCode(64), customerSegment(40), countryCode(8)

### 8.4 promotion-service: BatchCreateCouponsRequest missing date range validation ✅
- Added `@AssertTrue isDateRangeValid()` method

### 8.5 admin-service: UpsertFeatureFlagRequest missing range ✅
- Added `@Min(0) @Max(100)` on rolloutPercentage

---

## 9. PAGINATION - DONE

### 9.1 vendor-service: Admin endpoints paginated ✅
- Added paginated repository methods, updated service interface/impl, controllers return `Page<>`

### 9.2 poster-service: All list endpoints paginated ✅
- Added paginated repository methods, updated service interface/impl, controllers return `Page<>`

### 9.3 access-service: Staff/permission group endpoints paginated ✅
- Added paginated repository methods, updated service interface/impl, controllers return `Page<>`

### 9.4 wishlist-service: Wishlist items paginated ✅
- Added paginated repository method, updated service and controller

### 9.5 promotion-service: Coupon listing paginated ✅
- Added paginated repository method with join fetch, updated service and controller

### 9.6 order-service: findExpiredOrders() bounded ✅
- Added `Pageable` parameter, scheduler passes `PageRequest.of(0, 500)`

### 9.7 Global max page size enforced ✅
- Created `PaginationConfig.java` with `maxPageSize(100)` in all 10 services

---

## 10. REPOSITORY LOCKING (@Version) - DONE

### 10.1-10.9 Added @Version to all entities ✅
- Order, VendorOrder, Product, Poster, Vendor, Customer, PromotionCampaign, CouponCode, SystemConfig, FeatureFlag

### 10.10 Flash sale atomic increment ✅
- Added `incrementFlashSaleRedemptionCount()` with conditional atomic UPDATE in PromotionCampaignRepository

### 10.11 VendorOrder pessimistic lock ✅
- Added `findByIdForUpdate()` with `@Lock(PESSIMISTIC_WRITE)` to VendorOrderRepository, used in `updateVendorOrderStatus()`

---

## 11. DB INDEXING - DONE

### 11.1-11.15 All indexes added ✅
- OrderStatusAudit: (order_id, created_at DESC)
- VendorOrderStatusAudit: (vendor_order_id, created_at DESC)
- VendorOrder: (vendor_id, status, created_at DESC)
- Customer: (is_active), (created_at)
- CustomerAddress: (customer_id, deleted)
- AccessChangeAudit: (created_at)
- PlatformStaffAccess: (is_active, is_deleted, access_expires_at)
- VendorStaffAccess: (is_active, is_deleted, access_expires_at)
- CouponReservation: (customer_id, status), (coupon_code_id, customer_id, status)
- WishlistItem: (keycloak_id, product_id)
- Product: (vendor_id, is_active, is_deleted), (created_at)
- ProductCatalogRead: (selling_price), (main_category_slug)
- FeatureFlag: (enabled)

---

## 12. CODE DRIFTS - DONE

### 12.1 GlobalExceptionHandler error() helper standardized ✅
- access-service, poster-service, vendor-service refactored to return `Map<String, Object>`

### 12.2 Missing ServiceUnavailableException handler ✅
- Created exception class + handler in access-service and poster-service

### 12.3 Inconsistent logging in ValidationException handlers ✅
- Removed stack trace from poster-service and vendor-service

### 12.4 vendor-service ServiceUnavailableException log level ✅
- Changed from `log.warn()` to `log.error()`

### 12.5 Controller auth boilerplate ✅
- Added `requireUserSub()` helper to cart-service, order-service, customer-service controllers

### 12.6 VendorOrderSelfServiceController missing email verification ✅
- Added `X-User-Email-Verified` header check and `verifyEmailVerified()` method

### 12.7 promotion-service IllegalArgumentException handler ✅
- Replaced `ex.getMessage()` in response with generic `"Invalid request parameter"`

---

## 13. ARCHITECTURE DRIFTS - DONE

### 13.1 CachingConfigurerSupport deprecated ✅
- Changed to `implements CachingConfigurer` in product-service and promotion-service

---

## 14. LOGICAL ISSUES - DONE

### 14.1 VendorOrder pessimistic lock ✅
- Covered in 10.11
