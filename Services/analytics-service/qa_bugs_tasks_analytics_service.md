# QA Security Audit — analytics-service

**Auditor**: Principal Backend QA Architect & Senior Security Bug Hunter
**Date**: 2026-02-27
**Service**: `analytics-service` (port 8088)
**Codebase**: 77 Java files (10 clients, 3 services, 4 controllers, 5 configs, 29 client DTOs, 11 response DTOs, 4 exception classes)

---

## Summary

| Severity | Count |
|----------|-------|
| Critical | 0     |
| High     | 1     |
| Medium   | 3     |
| Low      | 3     |
| **Total**| **7** |

---

## BUG-ANA-001: Customer Analytics IDOR — Any Authenticated User Can View Any Customer's Insights

**Severity**: High
**Dimension**: Security & Access Control
**File**: `src/main/java/com/rumal/analytics_service/controller/CustomerAnalyticsController.java` (lines 19–26)

### Description

The `CustomerAnalyticsController` only verifies the internal auth header. It does **not** verify that the authenticated user's identity (`X-User-Sub`) matches the `customerId` path variable. The API Gateway's SecurityConfig routes `/analytics/customer/**` with `.authenticated()`, meaning any authenticated user (customer, vendor, admin) can access any other customer's analytics insights — order history, spending trends, and profile data — simply by changing the `customerId` in the URL.

### Current Code

```java
@GetMapping("/{customerId}/insights")
public CustomerInsightsResponse customerInsights(
        @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
        @PathVariable UUID customerId) {
    internalRequestVerifier.verify(internalAuth);
    return customerAnalyticsService.getCustomerInsights(customerId);
}
```

### Fix

```java
@GetMapping("/{customerId}/insights")
public CustomerInsightsResponse customerInsights(
        @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
        @RequestHeader(value = "X-User-Sub", required = false) String userSub,
        @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
        @PathVariable UUID customerId) {
    internalRequestVerifier.verify(internalAuth);
    verifyCustomerAccess(userSub, userRoles, customerId);
    return customerAnalyticsService.getCustomerInsights(customerId);
}

private static final Set<String> ADMIN_ROLES = Set.of("super_admin", "platform_staff");

private void verifyCustomerAccess(String userSub, String userRoles, UUID requestedCustomerId) {
    // Admins can view any customer's analytics
    if (userRoles != null && !userRoles.isBlank()) {
        for (String role : userRoles.split(",")) {
            if (ADMIN_ROLES.contains(role.trim().toLowerCase())) {
                return;
            }
        }
    }
    // Customers can only view their own analytics
    if (userSub == null || userSub.isBlank()) {
        throw new UnauthorizedException("User identification required");
    }
    try {
        UUID authenticatedUserId = UUID.fromString(userSub.trim());
        if (!authenticatedUserId.equals(requestedCustomerId)) {
            throw new UnauthorizedException("You can only view your own analytics");
        }
    } catch (IllegalArgumentException e) {
        throw new UnauthorizedException("Invalid user identification");
    }
}
```

---

## BUG-ANA-002: Gateway vs Analytics-Service Role Mismatch Blocks vendor_staff and platform_staff

**Severity**: Medium
**Dimension**: Security & Access Control
**Files**:
- `api-gateway/.../SecurityConfig.java` (lines 66–68)
- `analytics-service/.../AdminAnalyticsController.java` (line 22)
- `analytics-service/.../VendorAnalyticsController.java` (line 18)

### Description

The API Gateway is **more restrictive** than the analytics-service expects, silently blocking legitimate roles:

| Path Pattern | Gateway Allows | Analytics-Service Allows | Blocked Roles |
|---|---|---|---|
| `/analytics/admin/**` | `super_admin` | `super_admin`, `platform_staff` | `platform_staff` |
| `/analytics/vendor/**` | `super_admin`, `vendor_admin` | `super_admin`, `platform_staff`, `vendor_admin`, `vendor_staff` | `platform_staff`, `vendor_staff` |

`platform_staff` should have granular global access to admin analytics. `vendor_staff` should have granular access to their own vendor analytics. Both are blocked by the gateway before reaching the analytics-service.

### Fix — API Gateway SecurityConfig

```java
// BEFORE:
.pathMatchers("/analytics/admin/**", "/admin/dashboard/**").access(this::hasSuperAdminAccess)
.pathMatchers("/analytics/vendor/**").access(this::hasSuperAdminOrVendorAdminAccess)

// AFTER:
.pathMatchers("/admin/dashboard/**").access(this::hasSuperAdminAccess)
.pathMatchers("/analytics/admin/**").access(this::hasSuperAdminOrPlatformStaffAccess)
.pathMatchers("/analytics/vendor/**").access(this::hasAnyScopedAdminOrVendorAccess)
```

Where `hasAnyScopedAdminOrVendorAccess` is a new method (or reuse `hasAnyScopedAdminAccess` which already allows all four roles: super_admin, platform_staff, vendor_admin, vendor_staff — already defined in the gateway at lines 141–154):

```java
// The existing hasAnyScopedAdminAccess already covers all 4 needed roles:
.pathMatchers("/analytics/vendor/**").access(this::hasAnyScopedAdminAccess)
```

Note: `hasSuperAdminOrPlatformStaffAccess` already exists in the gateway (lines 129–139).

---

## BUG-ANA-003: VendorAnalyticsService and CustomerAnalyticsService Missing CompletableFuture Timeout

**Severity**: Medium
**Dimension**: Architecture & Resilience
**Files**:
- `src/main/java/com/rumal/analytics_service/service/VendorAnalyticsService.java` (lines 31–41)
- `src/main/java/com/rumal/analytics_service/service/CustomerAnalyticsService.java` (lines 28–32)

### Description

`AdminAnalyticsService` properly applies `.orTimeout(10, TimeUnit.SECONDS)` on all CompletableFuture calls to cap individual downstream call latency. `VendorAnalyticsService` (8 futures) and `CustomerAnalyticsService` (3 futures) do **not** apply any timeout.

Without the timeout, each future relies solely on the CB time limiter (6s) + retry (3 attempts × 500ms wait) = potential 18s+ per slow downstream call. The `allOf().join()` blocks the calling thread until the slowest future completes.

### Fix — VendorAnalyticsService

```java
@Cacheable(cacheNames = "vendorAnalytics", key = "#vendorId")
public VendorDashboardAnalytics getVendorDashboard(UUID vendorId) {
    var ordersFuture = CompletableFuture.supplyAsync(() -> safeCall(() -> orderClient.getVendorSummary(vendorId, 30), null), analyticsExecutor)
            .orTimeout(10, TimeUnit.SECONDS);
    var revenueTrendFuture = CompletableFuture.supplyAsync(() -> safeCall(() -> orderClient.getVendorRevenueTrend(vendorId, 30), List.<DailyRevenueBucket>of()), analyticsExecutor)
            .orTimeout(10, TimeUnit.SECONDS);
    var topProductsFuture = CompletableFuture.supplyAsync(() -> safeCall(() -> orderClient.getVendorTopProducts(vendorId, 10), List.<TopProductEntry>of()), analyticsExecutor)
            .orTimeout(10, TimeUnit.SECONDS);
    var productsFuture = CompletableFuture.supplyAsync(() -> safeCall(() -> productClient.getVendorSummary(vendorId), null), analyticsExecutor)
            .orTimeout(10, TimeUnit.SECONDS);
    var inventoryFuture = CompletableFuture.supplyAsync(() -> safeCall(() -> inventoryClient.getVendorHealth(vendorId), null), analyticsExecutor)
            .orTimeout(10, TimeUnit.SECONDS);
    var promotionsFuture = CompletableFuture.supplyAsync(() -> safeCall(() -> promotionClient.getVendorSummary(vendorId), null), analyticsExecutor)
            .orTimeout(10, TimeUnit.SECONDS);
    var reviewsFuture = CompletableFuture.supplyAsync(() -> safeCall(() -> reviewClient.getVendorSummary(vendorId), null), analyticsExecutor)
            .orTimeout(10, TimeUnit.SECONDS);
    var performanceFuture = CompletableFuture.supplyAsync(() -> safeCall(() -> vendorClient.getVendorPerformance(vendorId), null), analyticsExecutor)
            .orTimeout(10, TimeUnit.SECONDS);

    CompletableFuture.allOf(ordersFuture, revenueTrendFuture, topProductsFuture,
        productsFuture, inventoryFuture, promotionsFuture, reviewsFuture, performanceFuture).join();

    return new VendorDashboardAnalytics(
        ordersFuture.join(), revenueTrendFuture.join(), topProductsFuture.join(),
        productsFuture.join(), inventoryFuture.join(), promotionsFuture.join(),
        reviewsFuture.join(), performanceFuture.join()
    );
}
```

Add `import java.util.concurrent.TimeUnit;` to VendorAnalyticsService.

### Fix — CustomerAnalyticsService

```java
@Cacheable(cacheNames = "customerInsights", key = "#customerId")
public CustomerInsightsResponse getCustomerInsights(UUID customerId) {
    var orderSummaryFuture = CompletableFuture.supplyAsync(() -> safeCall(() -> orderClient.getCustomerSummary(customerId), null), analyticsExecutor)
            .orTimeout(10, TimeUnit.SECONDS);
    var spendingTrendFuture = CompletableFuture.supplyAsync(() -> safeCall(() -> orderClient.getCustomerSpendingTrend(customerId, 12), List.<MonthlySpendBucket>of()), analyticsExecutor)
            .orTimeout(10, TimeUnit.SECONDS);
    var profileFuture = CompletableFuture.supplyAsync(() -> safeCall(() -> customerClient.getProfileSummary(customerId), null), analyticsExecutor)
            .orTimeout(10, TimeUnit.SECONDS);

    CompletableFuture.allOf(orderSummaryFuture, spendingTrendFuture, profileFuture).join();

    return new CustomerInsightsResponse(
        orderSummaryFuture.join(), spendingTrendFuture.join(), profileFuture.join()
    );
}
```

Add `import java.util.concurrent.TimeUnit;` to CustomerAnalyticsService.

---

## BUG-ANA-004: LegacyDashboardSummary — Sequential Uncached Downstream Calls

**Severity**: Medium
**Dimension**: Architecture & Resilience
**File**: `src/main/java/com/rumal/analytics_service/service/AdminAnalyticsService.java` (lines 157–164)

### Description

`getLegacyDashboardSummary()` has two problems:

1. **No `@Cacheable`**: Every request hits 4 downstream services (order, vendor, product, promotion). All other admin analytics methods are cached.
2. **Sequential execution**: Calls 4 downstream services in sequence, not in parallel. With CB+retry, worst case: 4 × (3 retries × 6s CB timeout) = 72 seconds.

Compare with `getDashboardSummary()` which uses `@Cacheable` + parallel `CompletableFuture` + `.orTimeout(10s)`.

### Current Code

```java
public DashboardSummaryResponse getLegacyDashboardSummary() {
    var orderSummary = safeCall(() -> orderClient.getPlatformSummary(30), null);
    var vendorSummary = safeCall(() -> vendorClient.getPlatformSummary(), null);
    var productSummary = safeCall(() -> productClient.getPlatformSummary(), null);
    var promotionSummary = safeCall(() -> promotionClient.getPlatformSummary(), null);

    return buildLegacyDashboard(orderSummary, vendorSummary, productSummary, promotionSummary);
}
```

### Fix

```java
@Cacheable(cacheNames = "dashboardSummary", key = "'legacy'")
public DashboardSummaryResponse getLegacyDashboardSummary() {
    var ordersFuture = CompletableFuture.supplyAsync(() ->
            safeCall(() -> orderClient.getPlatformSummary(30), null), analyticsExecutor).orTimeout(10, TimeUnit.SECONDS);
    var vendorsFuture = CompletableFuture.supplyAsync(() ->
            safeCall(() -> vendorClient.getPlatformSummary(), null), analyticsExecutor).orTimeout(10, TimeUnit.SECONDS);
    var productsFuture = CompletableFuture.supplyAsync(() ->
            safeCall(() -> productClient.getPlatformSummary(), null), analyticsExecutor).orTimeout(10, TimeUnit.SECONDS);
    var promotionsFuture = CompletableFuture.supplyAsync(() ->
            safeCall(() -> promotionClient.getPlatformSummary(), null), analyticsExecutor).orTimeout(10, TimeUnit.SECONDS);

    CompletableFuture.allOf(ordersFuture, vendorsFuture, productsFuture, promotionsFuture).join();

    return buildLegacyDashboard(ordersFuture.join(), vendorsFuture.join(),
            productsFuture.join(), promotionsFuture.join());
}
```

---

## BUG-ANA-005: Vendor Leaderboard sortBy Cache Key Not Validated

**Severity**: Low
**Dimension**: Architecture & Resilience
**Files**:
- `src/main/java/com/rumal/analytics_service/controller/AdminAnalyticsController.java` (line 65)
- `src/main/java/com/rumal/analytics_service/service/AdminAnalyticsService.java` (line 110)

### Description

The `sortBy` query parameter is user-controlled with no whitelist validation. Each unique `sortBy` value creates a new Redis cache entry under the `vendorLeaderboard` cache. A malicious super_admin could send arbitrary `sortBy` values to pollute Redis with unbounded cache entries.

The `sortBy` is also directly concatenated into the downstream URL in `VendorAnalyticsClient.getLeaderboard()` (line 47), which could inject extra query parameters if the value contains `&` characters.

### Current Code

```java
@GetMapping("/vendor-leaderboard")
public AdminVendorLeaderboardResponse vendorLeaderboard(
        // ...
        @RequestParam(defaultValue = "ORDERS_COMPLETED") String sortBy) {
    verifyAdminAccess(internalAuth, userRoles);
    return adminAnalyticsService.getVendorLeaderboard(sortBy);
}
```

### Fix — AdminAnalyticsController

```java
private static final Set<String> ALLOWED_SORT_BY = Set.of(
    "ORDERS_COMPLETED", "AVERAGE_RATING", "FULFILLMENT_RATE", "DISPUTE_RATE", "REVENUE"
);

@GetMapping("/vendor-leaderboard")
public AdminVendorLeaderboardResponse vendorLeaderboard(
        @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
        @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
        @RequestParam(defaultValue = "ORDERS_COMPLETED") String sortBy) {
    verifyAdminAccess(internalAuth, userRoles);
    String normalizedSortBy = sortBy.trim().toUpperCase();
    if (!ALLOWED_SORT_BY.contains(normalizedSortBy)) {
        normalizedSortBy = "ORDERS_COMPLETED";
    }
    return adminAnalyticsService.getVendorLeaderboard(normalizedSortBy);
}
```

---

## BUG-ANA-006: All Clients Conflate HTTP Errors with Connectivity Failures

**Severity**: Low
**Dimension**: Architecture & Resilience
**Files**: All 10 client classes in `src/main/java/com/rumal/analytics_service/client/`

### Description

All 10 analytics clients catch `RestClientException` (which includes `RestClientResponseException` for 4xx/5xx HTTP responses) and wrap them uniformly as `ServiceUnavailableException`. This causes two problems:

1. **Circuit breaker pollution**: `ServiceUnavailableException` is not in the CB's `ignoreExceptions` list. A 404 from a downstream service is counted as a CB failure and retried 3 times. Enough 4xx errors could open the circuit, blocking all analytics calls to that service.
2. **Dead code**: `DownstreamHttpException` is defined in the exception package and configured as an `ignoreException` in the CB, but no client ever throws it.

### Example (OrderAnalyticsClient, same pattern in all clients)

```java
private <T> T get(String url, Class<T> type) {
    try {
        return restClient.get()
                .uri(url)
                .header("X-Internal-Auth", internalAuth)
                .retrieve()
                .body(type);
    } catch (RestClientException ex) {
        throw new ServiceUnavailableException("Order analytics service unavailable", ex);
    }
}
```

### Fix (apply to all 10 clients)

```java
import org.springframework.web.client.RestClientResponseException;

private <T> T get(String url, Class<T> type) {
    try {
        return restClient.get()
                .uri(url)
                .header("X-Internal-Auth", internalAuth)
                .retrieve()
                .body(type);
    } catch (RestClientResponseException ex) {
        throw new DownstreamHttpException(ex.getStatusCode(),
                "Order analytics HTTP error: " + ex.getStatusCode().value(), ex);
    } catch (RestClientException ex) {
        throw new ServiceUnavailableException("Order analytics service unavailable", ex);
    }
}
```

This ensures 4xx/5xx responses are wrapped as `DownstreamHttpException` (ignored by CB and retry), while connectivity failures remain as `ServiceUnavailableException` (recorded by CB and retried).

---

## BUG-ANA-007: HMAC Verification Silently Skipped When Headers Absent

**Severity**: Low
**Dimension**: Security & Access Control
**File**: `src/main/java/com/rumal/analytics_service/security/InternalRequestVerifier.java` (lines 46–47)

### Description

When `X-Internal-Signature` or `X-Internal-Timestamp` headers are absent, the HMAC verification is silently skipped. Only the shared secret check is performed. The HMAC provides replay protection (60s timestamp drift window) and path verification, both of which are bypassed when HMAC headers are omitted.

### Current Code

```java
if (signature == null || timestampStr == null) {
    return; // HMAC not yet deployed by caller — graceful
}
```

### Fix

Once all callers deploy HMAC signing, enforce the headers:

```java
if (signature == null || timestampStr == null) {
    throw new UnauthorizedException("Missing HMAC signature headers");
}
```

Until then, add monitoring to track the transition:

```java
if (signature == null || timestampStr == null) {
    log.warn("HMAC headers missing for {} {} — falling back to shared-secret-only auth",
            request.getMethod(), request.getRequestURI());
    return;
}
```

---

## Positive Findings (No Action Required)

| Area | Assessment |
|---|---|
| **Spring proxy bypass** | No self-invocation of `@Cacheable` or `@CacheEvict` methods within the same class |
| **Redis cache config** | Proper per-cache TTLs, serialization error recovery with corrupted entry eviction, startup cache clearing |
| **Virtual thread executor** | Correct use of `Executors.newVirtualThreadPerTaskExecutor()` with `destroyMethod = "shutdown"` |
| **Circuit breaker + retry** | Consistent pattern across all 10 clients with proper factory+registry usage |
| **Global exception handler** | Catches all exception types; generic `Exception` handler returns sanitized "Unexpected error" (no stack trace leak) |
| **Vendor tenant isolation** | `VendorAnalyticsController.verifyVendorAccess()` properly compares `X-Vendor-Id` header with requested `vendorId`; admins bypass tenant check |
| **Cache null values** | `disableCachingNullValues()` prevents caching `null` downstream responses |
| **No database layer** | Pure aggregation/BFF service with no JPA — no N+1, no transactional, no concurrency concerns |
