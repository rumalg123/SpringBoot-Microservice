# QA / Security / Architecture Audit — admin-service

**Auditor**: Principal Backend QA Architect & Senior Security Bug Hunter
**Date**: 2026-02-27
**Service**: `admin-service` (BFF / Admin Aggregation Layer)
**Severity Scale**: Critical > High > Medium > Low

---

## Executive Summary

The admin-service acts as a Backend-for-Frontend aggregation layer for all admin operations, delegating to access-service, order-service, vendor-service, and poster-service. It has strong RBAC enforcement via `AdminActorScopeService`, proper multi-tenancy scoping for vendor operations, and comprehensive idempotency protection for mutations. Resilience4j circuit breakers and retries are applied to all downstream clients.

**Findings**: 10 issues (0 Critical, 0 High, 4 Medium, 6 Low)

---

## BUG-ADM-001: OrderClient Retries 4xx HTTP Errors on Mutations

| Field | Value |
|---|---|
| **Severity** | Medium |
| **Category** | Architecture & Resilience |
| **File** | `src/main/java/com/rumal/admin_service/client/OrderClient.java` |
| **Lines** | 97–117 (updateOrderStatus), 119–134 (updateOrderNote), 187–215 (updateVendorOrderStatus) |

### Description

OrderClient's mutation methods only catch `RestClientException`, which is the superclass of `RestClientResponseException`. This means HTTP error responses (400 Bad Request, 404 Not Found, 422 Unprocessable Entity, etc.) from order-service are wrapped as `ServiceUnavailableException` and **retried by Resilience4j** up to 3 times.

In contrast, VendorClient and AccessClient correctly catch `RestClientResponseException` first and convert it to `DownstreamHttpException` (which is excluded from retries). OrderClient lacks this distinction.

**Impact**: If order-service returns 400 for an invalid status transition, admin-service retries 3 times before returning a misleading 503 "Service Unavailable" to the caller instead of the actual 400 error. On write paths, this wastes resources and delays the response.

### Code (Current — OrderClient.updateOrderStatus)
```java
// Line 97-117
return runOrderCall(() -> {
    RestClient rc = restClient;
    try {
        // ... mutation logic ...
    } catch (RestClientException ex) {
        throw new ServiceUnavailableException("Order service unavailable. Try again later.", ex);
    }
});
```

### Fix

Add `RestClientResponseException` handling before the generic `RestClientException` catch, matching the pattern used in VendorClient and AccessClient.

```java
// Apply to: updateOrderStatus (lines 97-117), updateOrderNote (119-134),
//           updateVendorOrderStatus (187-215)
return runOrderCall(() -> {
    RestClient rc = restClient;
    try {
        // ... existing mutation logic ...
    } catch (RestClientResponseException ex) {
        throw toDownstreamHttpException(ex);
    } catch (RestClientException ex) {
        throw new ServiceUnavailableException("Order service unavailable. Try again later.", ex);
    }
});
```

Add the helper method (matching VendorClient's pattern):

```java
private DownstreamHttpException toDownstreamHttpException(RestClientResponseException ex) {
    String body = ex.getResponseBodyAsString();
    String message;
    if (StringUtils.hasText(body)) {
        String compactBody = body.replaceAll("\\s+", " ").trim();
        if (compactBody.length() > 300) {
            compactBody = compactBody.substring(0, 300) + "...";
        }
        message = "Order service responded with " + ex.getStatusCode().value() + ": " + compactBody;
    } else {
        message = "Order service responded with " + ex.getStatusCode().value();
    }
    return new DownstreamHttpException(ex.getStatusCode(), message, ex);
}
```

Add the necessary import:
```java
import org.springframework.web.client.RestClientResponseException;
import org.springframework.util.StringUtils;
```

---

## BUG-ADM-002: Missing OptimisticLockingFailureException Handler

| Field | Value |
|---|---|
| **Severity** | Medium |
| **Category** | Data Integrity & Concurrency |
| **File** | `src/main/java/com/rumal/admin_service/exception/GlobalExceptionHandler.java` |
| **Lines** | 19–83 (entire handler) |

### Description

`FeatureFlag` and `SystemConfig` entities use `@Version` for optimistic locking. If two concurrent requests update the same entity, the second will throw `OptimisticLockingFailureException`. The `GlobalExceptionHandler` does not handle this exception, so it falls through to the generic `Exception` handler (line 64–73) and returns HTTP 500 "Unexpected error" instead of 409 Conflict.

### Fix

Add an explicit handler:

```java
@ExceptionHandler(org.springframework.orm.OptimisticLockingFailureException.class)
public ResponseEntity<?> optimisticLockConflict(org.springframework.orm.OptimisticLockingFailureException ex) {
    log.warn("Optimistic lock conflict: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(error(HttpStatus.CONFLICT, "Resource was modified by another request. Please retry."));
}
```

---

## BUG-ADM-003: Missing DataIntegrityViolationException Handler

| Field | Value |
|---|---|
| **Severity** | Medium |
| **Category** | Data Integrity & Concurrency |
| **File** | `src/main/java/com/rumal/admin_service/exception/GlobalExceptionHandler.java` |
| **Lines** | 19–83 (entire handler) |

### Description

`FeatureFlagService.upsert()` and `SystemConfigService.upsert()` use a read-then-write pattern: `findByFlagKey().orElseGet(() -> new FeatureFlag())` followed by `save()`. Under concurrent requests with the **same new key**, both threads may find no existing entity and attempt to insert, causing a unique constraint violation (`DataIntegrityViolationException`).

Without a handler, this surfaces as HTTP 500 "Unexpected error" instead of a meaningful 409 Conflict response.

### Fix

Add an explicit handler:

```java
@ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
public ResponseEntity<?> dataIntegrityViolation(org.springframework.dao.DataIntegrityViolationException ex) {
    log.warn("Data integrity violation: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(error(HttpStatus.CONFLICT, "Resource already exists or data conflict. Please retry."));
}
```

---

## BUG-ADM-004: Bulk Order Status Update Unbounded Processing Time

| Field | Value |
|---|---|
| **Severity** | Medium |
| **Category** | Architecture & Resilience |
| **File** | `src/main/java/com/rumal/admin_service/service/AdminOrderService.java` |
| **Lines** | 120–142 |

### Description

`bulkUpdateOrderStatus` processes up to 50 orders sequentially, each calling `orderClient.updateOrderStatus` wrapped in circuit breaker + retry. Per-call worst case: 3 retries × 500ms wait + 4s TimeLimiter timeout = ~5.5s. For 50 orders: 50 × 5.5s = **275 seconds**.

The API gateway's response timeout (typically 5-30s) will kill the client connection long before the bulk operation completes, leaving phantom operations executing server-side with no response to the caller.

### Code (Current)
```java
// Line 127-134
for (UUID orderId : orderIds) {
    try {
        orderClient.updateOrderStatus(orderId, status, internalAuth, userSub, userRoles);
        succeeded++;
    } catch (Exception e) {
        log.warn("Bulk status update failed for order={} status={}", orderId, status, e);
        errors.add(new BulkOperationResult.BulkItemError(orderId, e.getMessage()));
    }
}
```

### Fix

Add a cumulative deadline to fail fast before the gateway timeout:

```java
public BulkOperationResult bulkUpdateOrderStatus(
        List<UUID> orderIds, String status, String internalAuth,
        String userSub, String userRoles
) {
    int succeeded = 0;
    List<BulkOperationResult.BulkItemError> errors = new ArrayList<>();
    long deadline = System.currentTimeMillis() + 25_000; // 25s hard deadline

    for (UUID orderId : orderIds) {
        if (System.currentTimeMillis() > deadline) {
            log.warn("Bulk status update aborted after deadline — processed {}/{}", succeeded + errors.size(), orderIds.size());
            // Mark remaining as failed
            int currentIndex = succeeded + errors.size();
            for (int i = currentIndex; i < orderIds.size(); i++) {
                errors.add(new BulkOperationResult.BulkItemError(orderIds.get(i), "Aborted: processing deadline exceeded"));
            }
            break;
        }
        try {
            orderClient.updateOrderStatus(orderId, status, internalAuth, userSub, userRoles);
            succeeded++;
        } catch (Exception e) {
            log.warn("Bulk status update failed for order={} status={}", orderId, status, e);
            errors.add(new BulkOperationResult.BulkItemError(orderId, e.getMessage()));
        }
    }

    if (succeeded > 0) {
        adminOrderCacheVersionService.bumpAdminOrdersCache();
    }

    return new BulkOperationResult(orderIds.size(), succeeded, errors.size(), errors);
}
```

---

## BUG-ADM-005: HMAC Verification Silently Skipped

| Field | Value |
|---|---|
| **Severity** | Low |
| **Category** | Security & Access Control |
| **File** | `src/main/java/com/rumal/admin_service/security/InternalRequestVerifier.java` |
| **Lines** | 46–47 |

### Description

When `X-Internal-Signature` and `X-Internal-Timestamp` headers are absent, HMAC verification is silently skipped (returns without error). The shared-secret comparison at line 32 is still enforced, so this is defense-in-depth HMAC only. The comment says "HMAC not yet deployed by caller — graceful", indicating a transition period.

**Risk**: If HMAC deployment is complete across all callers, this silent skip should be replaced with enforcement. While the shared-secret provides basic authentication, HMAC adds replay protection (timestamp drift check) and request integrity (method + URI in signature).

### Fix

Once all callers send HMAC headers, change to enforcement:

```java
private void verifyHmacFromRequest() {
    var attrs = RequestContextHolder.getRequestAttributes();
    if (!(attrs instanceof ServletRequestAttributes sra)) {
        return;
    }
    HttpServletRequest request = sra.getRequest();
    String signature = request.getHeader("X-Internal-Signature");
    String timestampStr = request.getHeader("X-Internal-Timestamp");
    if (signature == null || timestampStr == null) {
        throw new UnauthorizedException("Missing HMAC signature or timestamp headers");
    }
    // ... rest of verification unchanged
}
```

---

## BUG-ADM-006: Vendor Onboarding Non-Atomic Membership Upsert

| Field | Value |
|---|---|
| **Severity** | Low |
| **Category** | Data Integrity & Concurrency |
| **File** | `src/main/java/com/rumal/admin_service/service/AdminVendorService.java` |
| **Lines** | 215–228 |

### Description

`upsertVendorMembership` performs: (1) list all vendor memberships, (2) check if user exists, (3) create or update. This is a TOCTOU (Time-Of-Check-Time-Of-Use) pattern. If two concurrent onboarding requests for the same email+vendor arrive with different idempotency keys, both could see no existing membership and both attempt to create, potentially producing a duplicate membership in vendor-service.

**Mitigating factor**: The idempotency filter protects `/admin/vendors/{id}/users/onboard` POST. If the same idempotency key is used, the second request is blocked. The race only occurs with different idempotency keys, which implies different callers or intentional retries with new keys.

### Fix

This is primarily a vendor-service concern (unique constraint on vendorId+keycloakUserId). As defense-in-depth, add an optimistic retry in admin-service:

```java
private Map<String, Object> upsertVendorMembership(UUID vendorId, String keycloakUserId, Map<String, Object> membershipRequest, String internalAuth) {
    List<Map<String, Object>> memberships = vendorClient.listVendorUsers(vendorId, internalAuth);
    Map<String, Object> existing = memberships.stream()
            .filter(m -> keycloakUserId.equalsIgnoreCase(stringValue(m.get("keycloakUserId"))))
            .findFirst()
            .orElse(null);

    if (existing == null) {
        try {
            return vendorClient.addVendorUser(vendorId, membershipRequest, internalAuth);
        } catch (DownstreamHttpException ex) {
            if (ex.getStatusCode().value() == 409) {
                // Concurrent create — re-list and update instead
                memberships = vendorClient.listVendorUsers(vendorId, internalAuth);
                existing = memberships.stream()
                        .filter(m -> keycloakUserId.equalsIgnoreCase(stringValue(m.get("keycloakUserId"))))
                        .findFirst()
                        .orElse(null);
                if (existing != null) {
                    UUID membershipId = parseUuid(existing.get("id"), "vendor membership id");
                    return vendorClient.updateVendorUser(vendorId, membershipId, membershipRequest, internalAuth);
                }
            }
            throw ex;
        }
    }

    UUID membershipId = parseUuid(existing.get("id"), "vendor membership id");
    return vendorClient.updateVendorUser(vendorId, membershipId, membershipRequest, internalAuth);
}
```

Add the import:
```java
import com.rumal.admin_service.exception.DownstreamHttpException;
```

---

## BUG-ADM-007: Dashboard Summary Returns Hardcoded Zero Counts

| Field | Value |
|---|---|
| **Severity** | Low |
| **Category** | Logic & Runtime |
| **File** | `src/main/java/com/rumal/admin_service/service/DashboardService.java` |
| **Lines** | 53–57 |

### Description

`DashboardSummaryResponse` includes fields for `totalVendors`, `activeVendors`, `totalProducts`, and `activePromotions`, but `DashboardService.getSummary()` always returns `0` for these fields (line 55). The DTO exposes these as real metrics to the frontend, which could mislead dashboard consumers into thinking there are no vendors/products/promotions.

### Code (Current)
```java
// Line 53-57
return new DashboardSummaryResponse(
        totalOrders, pendingOrders, processingOrders, completedOrders, cancelledOrders,
        0, 0, 0, 0,   // totalVendors, activeVendors, totalProducts, activePromotions
        ordersByStatus, Instant.now()
);
```

### Fix

Either populate these from vendor-service and product-service, or remove the fields from the DTO to avoid misleading consumers. If keeping as placeholder, document explicitly:

```java
return new DashboardSummaryResponse(
        totalOrders, pendingOrders, processingOrders, completedOrders, cancelledOrders,
        -1, -1, -1, -1,  // -1 = not yet implemented
        ordersByStatus, Instant.now()
);
```

---

## BUG-ADM-008: Audit Log Filters Are Mutually Exclusive

| Field | Value |
|---|---|
| **Severity** | Low |
| **Category** | Logic & Runtime |
| **File** | `src/main/java/com/rumal/admin_service/service/AdminAuditService.java` |
| **Lines** | 46–58 |

### Description

`listAuditLogs` applies filters in a priority chain (if-else if-else if). Only one filter is used per query. This means searching for "audit logs by actor X within date range Y" is impossible — the actor filter takes priority and ignores the date range.

### Code (Current)
```java
// Lines 48-58
if (actorKeycloakId != null && !actorKeycloakId.isBlank()) {
    result = auditLogRepository.findByActorKeycloakId(actorKeycloakId.trim(), pageable);
} else if (resourceType != null && resourceId != null) {
    result = auditLogRepository.findByResourceTypeAndResourceId(resourceType, resourceId, pageable);
} else if (action != null && !action.isBlank()) {
    result = auditLogRepository.findByAction(action.trim(), pageable);
} else if (from != null && to != null) {
    result = auditLogRepository.findByCreatedAtBetween(from, to, pageable);
} else {
    result = auditLogRepository.findAll(pageable);
}
```

### Fix

Use JPA Specifications for composable filtering:

```java
@Transactional(readOnly = true)
public PageResponse<AdminAuditLogResponse> listAuditLogs(
        String actorKeycloakId, String action, String resourceType, String resourceId,
        Instant from, Instant to, int page, int size
) {
    size = Math.min(size, 100);
    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

    Specification<AdminAuditLog> spec = Specification.where(null);
    if (actorKeycloakId != null && !actorKeycloakId.isBlank()) {
        spec = spec.and((root, q, cb) -> cb.equal(root.get("actorKeycloakId"), actorKeycloakId.trim()));
    }
    if (resourceType != null && resourceId != null) {
        spec = spec.and((root, q, cb) -> cb.and(
                cb.equal(root.get("resourceType"), resourceType),
                cb.equal(root.get("resourceId"), resourceId)
        ));
    }
    if (action != null && !action.isBlank()) {
        spec = spec.and((root, q, cb) -> cb.equal(root.get("action"), action.trim()));
    }
    if (from != null) {
        spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
    }
    if (to != null) {
        spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to));
    }

    Page<AdminAuditLog> result = auditLogRepository.findAll(spec, pageable);
    return toPageResponse(result);
}
```

This requires `AdminAuditLogRepository` to extend `JpaSpecificationExecutor<AdminAuditLog>`.

---

## BUG-ADM-009: Keycloak Admin Client Created Per Call Without Token Caching

| Field | Value |
|---|---|
| **Severity** | Low |
| **Category** | Architecture & Resilience |
| **File** | `src/main/java/com/rumal/admin_service/auth/KeycloakVendorAdminManagementService.java` |
| **Lines** | 401–409 |

### Description

`newAdminClient()` creates a brand new `Keycloak` instance (including OAuth2 client_credentials token exchange) on every method invocation. For operations like `logoutUserSessionsBulk`, the client is correctly shared across all logouts within a single call. But for independent invocations (e.g., `searchUsers`, multiple `ensureVendorAdminUser` calls), each creates a separate token exchange.

**Impact**: Under high admin activity, this causes unnecessary load on Keycloak's token endpoint. Each `Keycloak` instance establishes new HTTP connections and performs a full client_credentials grant.

### Code (Current)
```java
// Lines 401-408
private Keycloak newAdminClient() {
    return KeycloakBuilder.builder()
            .serverUrl(serverUrl)
            .realm(adminRealm)
            .clientId(clientId)
            .clientSecret(clientSecret)
            .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
            .build();
}
```

### Fix

Use a shared singleton Keycloak client. The Keycloak admin client handles token refresh internally:

```java
private volatile Keycloak sharedAdminClient;

private Keycloak getOrCreateAdminClient() {
    Keycloak client = sharedAdminClient;
    if (client != null) {
        return client;
    }
    synchronized (this) {
        if (sharedAdminClient == null) {
            sharedAdminClient = KeycloakBuilder.builder()
                    .serverUrl(serverUrl)
                    .realm(adminRealm)
                    .clientId(clientId)
                    .clientSecret(clientSecret)
                    .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                    .build();
        }
        return sharedAdminClient;
    }
}
```

Then replace `try (Keycloak keycloak = newAdminClient())` with:
```java
Keycloak keycloak = getOrCreateAdminClient();
```

**Note**: Remove the try-with-resources since the shared client should not be closed per-call.

---

## BUG-ADM-010: Gateway Blocks Platform Staff from Dashboard Despite Service Allowing It

| Field | Value |
|---|---|
| **Severity** | Low |
| **Category** | Logic & Runtime |
| **Files** | `admin-service: src/main/java/com/rumal/admin_service/controller/AdminDashboardController.java:29-31` |
| | `api-gateway: src/main/java/com/rumal/api_gateway/config/SecurityConfig.java:66` |

### Description

`AdminDashboardController` allows both `super_admin` and `platform_staff` to access the dashboard (lines 29-31). However, the API Gateway's SecurityConfig at line 66 maps `/admin/dashboard/**` to `hasSuperAdminAccess`, which requires the `super_admin` role exclusively.

This means the platform_staff role check in admin-service is dead code — platform_staff users are rejected at the gateway before reaching admin-service.

### Code (AdminDashboardController — lines 29-31)
```java
if (!adminActorScopeService.hasRole(userRoles, "super_admin")
        && !adminActorScopeService.hasRole(userRoles, "platform_staff")) {
    throw new UnauthorizedException("Insufficient permissions for dashboard access");
}
```

### Code (Gateway SecurityConfig — line 66)
```java
.pathMatchers("/analytics/admin/**", "/admin/dashboard/**").access(this::hasSuperAdminAccess)
```

### Fix

**Option A** — If platform_staff should access the dashboard, update the gateway:
```java
.pathMatchers("/admin/dashboard/**").access(this::hasSuperAdminOrPlatformStaffAccess)
```

**Option B** — If only super_admin should access the dashboard, simplify the admin-service check:
```java
if (!adminActorScopeService.hasRole(userRoles, "super_admin")) {
    throw new UnauthorizedException("Insufficient permissions for dashboard access");
}
```

---

## Positive Findings (No Action Required)

| Area | Finding |
|---|---|
| **Tenant Isolation** | `AdminActorScopeService` correctly resolves vendor-scoped IDs for vendor_admin and vendor_staff across orders, vendor staff management, and access audit. All resolution methods validate membership against downstream services. |
| **RBAC Enforcement** | Every controller endpoint verifies `X-Internal-Auth` via `InternalRequestVerifier` and applies role-based authorization through `AdminActorScopeService`. Platform staff operations check granular permissions (e.g., `PLATFORM_ORDERS_MANAGE`). |
| **Circuit Breaker + Retry** | All 4 downstream clients (OrderClient, VendorClient, AccessClient, PosterClient) wrap calls with Resilience4j circuit breakers and retry. `DownstreamHttpException` is correctly excluded from both circuit breaker failure counting and retry logic (except OrderClient — see BUG-ADM-001). |
| **Idempotency** | `AdminMutationIdempotencyFilter` protects critical mutation paths with Redis-backed request deduplication. PENDING/DONE state machine, request body hashing, and oversized body bypass are all properly implemented. |
| **Cache Invalidation** | `AdminOrderCacheVersionService` uses Redis INCR for version-based cache invalidation — every order mutation bumps the version, causing cache misses on subsequent reads. TTL-based expiration (30s) provides a safety net. |
| **Optimistic Locking** | `FeatureFlag` and `SystemConfig` entities use `@Version` for concurrent modification detection. |
| **Keycloak Session Revocation** | Vendor deactivation/deletion triggers bulk Keycloak session revocation for all vendor principals (admins + staff). |
| **Connection Pooling** | `HttpClientConfig` configures Apache HttpClient5 with proper connection pooling, idle eviction, and configurable timeouts. |
| **Error Handling** | `GlobalExceptionHandler` properly differentiates between `UnauthorizedException`, `ServiceUnavailableException`, `DownstreamHttpException`, `ResponseStatusException`, and validation errors. Generic catch-all suppresses stack traces from client responses. |
| **Vendor Delete Safety** | Legacy `DELETE /admin/vendors/{id}` is disabled with 405 Method Not Allowed. The 2-step `delete-request` → `confirm-delete` flow prevents accidental deletions. |
