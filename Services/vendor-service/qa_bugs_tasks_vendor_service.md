# QA Bugs & Tasks — vendor-service

> **Auditor**: Principal Backend QA Architect & Senior Security Bug Hunter
> **Service**: `vendor-service` (port 8090)
> **Date**: 2026-02-27
> **Findings**: 4 total — 0 Critical, 0 High, 2 Medium, 2 Low

---

## Domain Map

| Layer | Component | Key Responsibility |
|---|---|---|
| Controller | `VendorController` (`/vendors`) | Public storefront: list active vendors, slug availability, get vendor by ID or slug |
| Controller | `AdminVendorController` (`/admin/vendors`) | Admin: CRUD, lifecycle (delete-request, confirm-delete, stop/resume orders, restore), verification (approve/reject), metrics, user management |
| Controller | `VendorSelfServiceController` (`/vendors/me`) | Vendor self-service: get/update vendor, payout config, request verification, stop/resume orders |
| Controller | `InternalVendorAccessController` (`/internal/vendors/access`) | Internal: membership lookup by keycloakId, operational state (single + batch), vendor names |
| Controller | `InternalVendorAnalyticsController` (`/internal/vendors/analytics`) | Internal: platform summary, leaderboard, vendor performance |
| Service | `VendorServiceImpl` | Core vendor CRUD, lifecycle, user management, self-service, verification workflow, metrics |
| Service | `VendorAnalyticsService` | Platform summary, leaderboard, vendor performance analytics |
| Service | `SlugUtils` | URL-friendly slug generation with normalization |
| Repository | `VendorRepository` | Vendor CRUD, slug queries, pessimistic lock (`findByIdForUpdate`), analytics aggregates |
| Repository | `VendorUserRepository` | User CRUD, membership lookups with `@EntityGraph`, keycloakId access queries |
| Repository | `VendorPayoutConfigRepository` | Payout config CRUD |
| Repository | `VendorLifecycleAuditRepository` | Audit trail queries |
| Entity | `Vendor` | `@Version`, slug (unique), status/active/acceptingOrders/verified flags, metrics, policies, categories |
| Entity | `VendorUser` | `UK(vendor_id, keycloak_user_id)`, role (OWNER/MANAGER), active flag |
| Entity | `VendorPayoutConfig` | `@OneToOne` Vendor (unique FK), bank details, payout schedule |
| Entity | `VendorLifecycleAudit` | `@ManyToOne` Vendor, action enum, actor info, audit trail |
| Client | `OrderLifecycleClient` | Fetch vendor deletion check from order-service (CB + Retry AOP) |
| Client | `ProductCatalogAdminClient` | Product cache eviction + bulk deactivation in product-service (CB + Retry AOP) |
| Config | `CacheConfig` | Caffeine cache: `vendorOperationalState` (15s TTL, max 10,000 entries) |
| Config | `VendorLifecycleIdempotencyFilter` | Redis-backed idempotency for lifecycle mutations + self-service mutations |
| Security | `InternalRequestVerifier` | HMAC-based internal auth for admin/internal endpoints |

---

## BUG-VENDOR-001 — Resilience4j Retries Broken for Both Clients

| Field | Value |
|---|---|
| **Severity** | **Medium** |
| **Category** | Architecture & Resilience |
| **Affected Files** | `client/OrderLifecycleClient.java`, `client/ProductCatalogAdminClient.java`, `application.yaml` |
| **Lines** | `OrderLifecycleClient.java:39–43`, `ProductCatalogAdminClient.java:37–41, 54–58`, `application.yaml:62–74` |

### Description

The Resilience4j retry configuration specifies retryable exceptions:

```yaml
retryExceptions:
  - java.io.IOException
  - org.springframework.web.client.ResourceAccessException
  - org.springframework.web.client.HttpServerErrorException
```

However, both clients catch `RestClientException` (the parent of both `ResourceAccessException` and `HttpServerErrorException`) and **re-wrap** it as `ServiceUnavailableException` **before** the exception reaches the Retry AOP aspect:

```java
// OrderLifecycleClient.java:39–43
} catch (RestClientResponseException ex) {
    throw new ServiceUnavailableException("Order service deletion check failed (" + ex.getStatusCode().value() + ")", ex);
} catch (RestClientException | IllegalStateException ex) {
    throw new ServiceUnavailableException("Order service unavailable for vendor deletion check", ex);
}
```

```java
// ProductCatalogAdminClient.java:37–41
} catch (RestClientResponseException ex) {
    throw new ServiceUnavailableException("Product service cache eviction failed (" + ex.getStatusCode().value() + ")", ex);
} catch (RestClientException | IllegalStateException ex) {
    throw new ServiceUnavailableException("Product service unavailable for cache eviction", ex);
}
```

Since `ServiceUnavailableException` is **not** in the `retryExceptions` list, the Retry aspect sees it and does **not** retry. Transient connectivity failures and 5xx responses immediately surface as errors with zero retries.

**Compounded impact with vendor deletion**: When `confirmDelete()` calls `productCatalogAdminClient.deactivateAllByVendor()`, a transient failure is not retried. The circuit breaker fallback silently swallows the error (logs and returns void). The vendor is soft-deleted but all their products remain **active and visible** in the storefront. There is no outbox or reconciliation mechanism to eventually complete the product deactivation.

### Fix

Add `ServiceUnavailableException` to the retry configuration for both instances.

**`application.yaml`** — Replace the retry configuration for `orderService` (lines 57–65):
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
          - com.rumal.vendor_service.exception.ServiceUnavailableException
```

Apply the same change to `productService` (lines 66–74).

---

## BUG-VENDOR-002 — Missing Cache Eviction on Verification Approval

| Field | Value |
|---|---|
| **Severity** | **Medium** |
| **Category** | Data Integrity |
| **Affected Files** | `service/VendorServiceImpl.java` |
| **Lines** | `VendorServiceImpl.java:968–985` |

### Description

The `approveVerification()` method changes `vendor.verified` from `false` to `true` and `verificationStatus` to `VERIFIED`. These fields directly affect the `VendorOperationalStateResponse` (which includes `verified` and `storefrontVisible`), and `storefrontVisible` is computed via `isStorefrontVisible()` which **requires** `vendor.isVerified()`:

```java
private boolean isStorefrontVisible(Vendor vendor) {
    return vendor != null
            && !vendor.isDeleted()
            && vendor.isActive()
            && vendor.getStatus() == VendorStatus.ACTIVE
            && vendor.isAcceptingOrders()
            && vendor.isVerified();  // <-- Changed by approveVerification
}
```

However, `approveVerification()` does **not** have a `@CacheEvict` annotation for the `vendorOperationalState` Caffeine cache:

```java
@Override
@Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
// Missing: @CacheEvict(cacheNames = "vendorOperationalState", key = "#vendorId")
public VendorResponse approveVerification(UUID vendorId, AdminVerificationActionRequest request, ...) {
```

Compare with other state-changing methods that **do** evict the cache:
- `update()` — `@CacheEvict(cacheNames = "vendorOperationalState", key = "#id")`
- `stopReceivingOrders()` — `@CacheEvict`
- `resumeReceivingOrders()` — `@CacheEvict`
- `restore()` — `@CacheEvict`
- `confirmDelete()` — `@CacheEvict`

After verification approval, the Caffeine cache retains the stale entry (`verified=false`, `storefrontVisible=false`) for up to 15 seconds (the cache TTL). During that window, other services calling `getOperationalState(vendorId)` — such as cart-service checking if a vendor is operational before checkout — see the vendor as unverified and not storefront-visible, potentially blocking operations for a vendor that was just approved.

### Current Code

**`VendorServiceImpl.java:968–985`**:
```java
@Override
@Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
public VendorResponse approveVerification(UUID vendorId, AdminVerificationActionRequest request, String actorSub, String actorRoles) {
    Vendor vendor = getNonDeletedVendor(vendorId);
    if (vendor.getVerificationStatus() != VerificationStatus.PENDING_VERIFICATION) {
        throw new ValidationException("Can only approve verification when status is PENDING_VERIFICATION, current: " + vendor.getVerificationStatus());
    }
    vendor.setVerificationStatus(VerificationStatus.VERIFIED);
    vendor.setVerified(true);
    vendor.setVerifiedAt(Instant.now());
    // ...
    Vendor saved = vendorRepository.save(vendor);
    // ...
    return toVendorResponse(saved);
}
```

### Fix

Add `@CacheEvict` to `approveVerification()`.

**`VendorServiceImpl.java:968–970`** — Replace:
```java
@Override
@Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
public VendorResponse approveVerification(UUID vendorId, AdminVerificationActionRequest request, String actorSub, String actorRoles) {
```

With:
```java
@Override
@Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
@CacheEvict(cacheNames = "vendorOperationalState", key = "#vendorId")
public VendorResponse approveVerification(UUID vendorId, AdminVerificationActionRequest request, String actorSub, String actorRoles) {
```

Also add `@CacheEvict` to `rejectVerification()` for consistency (a previously-verified vendor re-requesting and getting rejected is not currently possible in the state machine, but this future-proofs it):

**`VendorServiceImpl.java:987–989`** — Replace:
```java
@Override
@Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
public VendorResponse rejectVerification(UUID vendorId, AdminVerificationActionRequest request, String actorSub, String actorRoles) {
```

With:
```java
@Override
@Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
@CacheEvict(cacheNames = "vendorOperationalState", key = "#vendorId")
public VendorResponse rejectVerification(UUID vendorId, AdminVerificationActionRequest request, String actorSub, String actorRoles) {
```

---

## BUG-VENDOR-003 — Duplicate VendorUser Race Condition Returns 500 Instead of Meaningful Error

| Field | Value |
|---|---|
| **Severity** | **Low** |
| **Category** | Data Integrity & Concurrency |
| **Affected Files** | `service/VendorServiceImpl.java`, `exception/GlobalExceptionHandler.java` |
| **Lines** | `VendorServiceImpl.java:384–394`, `GlobalExceptionHandler.java:64–68` |

### Description

The `addVendorUser()` method uses a check-then-act pattern to prevent duplicate vendor user memberships:

```java
if (vendorUserRepository.existsByVendorIdAndKeycloakUserId(vendorId, keycloakUserId)) {
    throw new ValidationException("Vendor user already exists for vendor and keycloakUserId");
}
```

The `VendorUser` entity has a database unique constraint `uk_vendor_users_vendor_keycloak` on `(vendor_id, keycloak_user_id)`, so data integrity is protected. However, under concurrent requests, two threads can both pass the `existsBy` check, and the second `save()` throws a `DataIntegrityViolationException`.

`DataIntegrityViolationException` is **not** explicitly handled in `GlobalExceptionHandler`. It falls through to the catch-all handler:

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<?> handleOther(Exception ex) {
    log.error("Unhandled vendor-service exception", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(error(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error"));
}
```

The client receives a **500 "Unexpected error"** instead of a meaningful **409 Conflict** response indicating that the membership already exists.

### Fix

Add an explicit handler for `DataIntegrityViolationException` in `GlobalExceptionHandler.java`.

Add after `handleOptimisticLock` (~line 62):
```java
@ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
public ResponseEntity<?> handleDataIntegrity(org.springframework.dao.DataIntegrityViolationException ex) {
    log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(error(HttpStatus.CONFLICT, "Resource already exists or constraint violated. Please retry."));
}
```

---

## BUG-VENDOR-004 — Self-Service Payout Config Modification Not Restricted by Vendor User Role

| Field | Value |
|---|---|
| **Severity** | **Low** |
| **Category** | Security & Access Control |
| **Affected Files** | `service/VendorServiceImpl.java`, `controller/VendorSelfServiceController.java` |
| **Lines** | `VendorServiceImpl.java:782–800, 863–876`, `VendorSelfServiceController.java:63–72` |

### Description

The `resolveVendorForKeycloakUser()` method resolves the vendor for a keycloakId by finding active memberships, but does **not** check the user's `VendorUserRole` (OWNER vs MANAGER):

```java
private Vendor resolveVendorForKeycloakUser(String keycloakUserId, UUID vendorIdHint) {
    String normalized = normalizeRequired(keycloakUserId, "keycloakUserId", 120);
    List<VendorUser> memberships = vendorUserRepository
            .findAccessibleMembershipsByKeycloakUser(normalized);
    // ... selects vendor from memberships — NO role check
}
```

All self-service endpoints use `resolveVendorForKeycloakUser()` without any role differentiation. A user with the **MANAGER** role can perform the same operations as an **OWNER**:

| Endpoint | Operation | Should require OWNER? |
|---|---|---|
| `PUT /vendors/me/payout-config` | Modify bank account/payout details | Yes |
| `PUT /vendors/me` | Update vendor name, slug, contact info | Potentially |
| `POST /vendors/me/request-verification` | Submit verification request | Potentially |

The most sensitive operation is payout config modification — a MANAGER should not be able to change bank account details, routing codes, or tax IDs. This is a financial security concern: a malicious or compromised MANAGER account could redirect payouts to a different bank account.

### Fix

Add a role check helper and enforce OWNER role on sensitive operations.

**Step 1** — Add a role-aware resolver to `VendorServiceImpl.java`. Add after `resolveVendorForKeycloakUser` (~line 800):

```java
private Vendor resolveVendorOwner(String keycloakUserId, UUID vendorIdHint) {
    String normalized = normalizeRequired(keycloakUserId, "keycloakUserId", 120);
    List<VendorUser> memberships = vendorUserRepository
            .findAccessibleMembershipsByKeycloakUser(normalized);
    if (memberships.isEmpty()) {
        throw new ResourceNotFoundException("No active vendor membership found for user");
    }

    VendorUser membership;
    if (memberships.size() == 1) {
        membership = memberships.get(0);
    } else {
        if (vendorIdHint == null) {
            throw new ValidationException("Multiple vendor memberships found. Specify vendorId query parameter.");
        }
        membership = memberships.stream()
                .filter(m -> vendorIdHint.equals(m.getVendor().getId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("No active vendor membership found for vendorId: " + vendorIdHint));
    }

    if (membership.getRole() != VendorUserRole.OWNER) {
        throw new UnauthorizedException("Only vendor owners can perform this operation");
    }
    return membership.getVendor();
}
```

**Step 2** — Use `resolveVendorOwner` for payout config operations. In `upsertPayoutConfig()`, replace line 866:

```java
Vendor vendor = resolveVendorOwner(keycloakUserId, vendorIdHint);
```

And in `getPayoutConfig()`, replace line 857:
```java
Vendor vendor = resolveVendorOwner(keycloakUserId, vendorIdHint);
```

**Step 3** — Import `VendorUserRole` and `UnauthorizedException` if not already imported (both are already imported in VendorServiceImpl).

---

## Summary

| ID | Severity | Category | Title |
|---|---|---|---|
| BUG-VENDOR-001 | Medium | Architecture & Resilience | Resilience4j retries broken for both clients — `ServiceUnavailableException` not in retryExceptions |
| BUG-VENDOR-002 | Medium | Data Integrity | Missing `@CacheEvict` on `approveVerification` — stale operational state for up to 15s after verification |
| BUG-VENDOR-003 | Low | Data Integrity & Concurrency | Duplicate VendorUser race condition returns 500 instead of 409 Conflict |
| BUG-VENDOR-004 | Low | Security & Access Control | Self-service payout config modification not restricted by vendor user role (OWNER vs MANAGER) |
