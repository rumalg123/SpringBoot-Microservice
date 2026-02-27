# Access Service - Deep Audit Report

## Service Summary

| Attribute | Value |
|-----------|-------|
| Framework | Spring Boot 4.0.3 (WebMVC, JPA, Caffeine, Redis) |
| Auth | Internal shared secret (`X-Internal-Auth`) + optional HMAC |
| DB | PostgreSQL, `create-drop` (intentional) |
| Entities | PlatformStaffAccess, VendorStaffAccess, PermissionGroup, AccessChangeAudit, ActiveSession, ApiKey |
| Locking | Optimistic (`@Version`) on PlatformStaffAccess, VendorStaffAccess |
| Caching | Caffeine (20s TTL) on platform/vendor access lookups |
| Scheduling | AccessExpiryScheduler (60s interval) |

---

## BUG-AC-001: No Tenant Isolation on Vendor-Scoped Operations

| Field | Value |
|-------|-------|
| **Severity** | **High** |
| **Category** | Security / Access Control |
| **Files** | `controller/AdminVendorStaffController.java`, `service/AccessServiceImpl.java` |

### Description

The access-service provides vendor staff CRUD operations that are called by admin-service (which is the gateway-facing service for `/admin/vendor-staff/**`). Per the gateway SecurityConfig, `vendor_admin` users can access these endpoints.

However, the access-service performs **zero tenant-scoping enforcement**. Every vendor staff operation accepts arbitrary vendor IDs with no validation against the caller's tenant:

**1. List all vendor staff (line 36-44 of AdminVendorStaffController):**
```java
@GetMapping
public Page<VendorStaffAccessResponse> listAll(
        @RequestParam(required = false) UUID vendorId, ...
```
If `vendorId` is null, `AccessServiceImpl.listVendorStaff()` (line 302-307) calls `listAllVendorStaff()` which returns **every vendor's staff records**.

**2. Get by ID (line 55-62):**
```java
return accessService.getVendorStaffById(id);
```
`getVendorStaffById` (line 320-323) uses `findById(id)` with **no vendor filter**. Any vendor staff record from any vendor is returned.

**3. Update by ID (line 77-88):**
```java
return accessService.updateVendorStaff(id, request, ...);
```
`updateVendorStaff` (line 362-378) fetches by `findByIdAndDeletedFalse(id)` — **no vendor filter**. A request could modify another vendor's staff permissions.

**4. Delete by ID (line 90-101):**
Same pattern — `findByIdAndDeletedFalse(id)` with no vendor scope.

**5. Restore by ID (line 103-113):**
Same pattern — `findById(id)` with no vendor scope.

**6. List deleted (line 44-53):**
```java
return accessService.listDeletedVendorStaff(pageable);
```
Returns ALL deleted vendor staff records across ALL vendors with no vendor filter.

**7. Audit log (AdminAccessAuditController line 26-42):**
The optional `vendorId` parameter is not enforced — a vendor_admin could omit it or pass a competitor's vendor ID.

### Impact

If admin-service has any bug or missing validation in its vendor-scoping logic, a `vendor_admin` for Vendor A could:
- Read all staff records for Vendor B
- Modify Vendor B's staff permissions (privilege escalation across tenants)
- Delete Vendor B's staff access
- Read audit logs for other vendors
- View all deleted staff records platform-wide

### Fix

Add mandatory vendor-scoping at the access-service level as defense-in-depth. Accept `X-Vendor-Id` header from the caller and enforce it on all vendor-scoped operations.

```java
// AdminVendorStaffController.java — add to all vendor-scoped endpoints:

@GetMapping
public Page<VendorStaffAccessResponse> listAll(
        @RequestHeader(INTERNAL_HEADER) String internalAuth,
        @RequestHeader(value = "X-Caller-Vendor-Id", required = false) UUID callerVendorId,
        @RequestParam(required = false) UUID vendorId,
        @PageableDefault(size = 20, sort = "email") Pageable pageable
) {
    internalRequestVerifier.verify(internalAuth);
    UUID effectiveVendorId = callerVendorId != null ? callerVendorId : vendorId;
    return accessService.listVendorStaff(effectiveVendorId, pageable);
}

// AccessServiceImpl.java — add vendor-scoped lookups for update/delete/restore:

@Override
@Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
public VendorStaffAccessResponse updateVendorStaff(UUID id, UpsertVendorStaffAccessRequest request,
        String actorSub, String actorRoles, String reason) {
    VendorStaffAccess entity = vendorStaffAccessRepository.findByIdAndDeletedFalse(id)
            .orElseThrow(() -> new ResourceNotFoundException("Vendor staff not found: " + id));
    // Add: verify the entity belongs to the request's vendorId
    if (!entity.getVendorId().equals(request.vendorId())) {
        throw new ValidationException("Vendor staff does not belong to the specified vendor");
    }
    // ... rest of method
}

// Same pattern for softDeleteVendorStaff and restoreVendorStaff —
// accept vendorId parameter and verify entity.getVendorId().equals(vendorId)
```

Also add vendor scoping to `listDeletedVendorStaff`:
```java
Page<VendorStaffAccessResponse> listDeletedVendorStaff(UUID vendorId, Pageable pageable);
```
With repository method:
```java
Page<VendorStaffAccess> findByVendorIdAndDeletedTrue(UUID vendorId, Pageable pageable);
```

---

## BUG-AC-002: HMAC Verification Silently Skipped When Headers Missing

| Field | Value |
|-------|-------|
| **Severity** | **Medium** |
| **Category** | Security |
| **File** | `security/InternalRequestVerifier.java` |
| **Lines** | 46-47 |

### Description

```java
private void verifyHmacFromRequest() {
    // ...
    String signature = request.getHeader("X-Internal-Signature");
    String timestampStr = request.getHeader("X-Internal-Timestamp");
    if (signature == null || timestampStr == null) {
        return; // HMAC not yet deployed by caller — graceful
    }
    // ... HMAC verification ...
}
```

The HMAC layer that adds replay protection (timestamp + signature over method + path) is **completely optional**. If the `X-Internal-Signature` and `X-Internal-Timestamp` headers are absent, verification is silently skipped.

This means the only effective security is the shared secret comparison (line 32). The HMAC provides no actual protection because an attacker can simply omit the headers.

An attacker who obtains the shared secret (from any compromised service, environment variable leak, or log exposure) gets permanent access to all internal endpoints without time-bounding or request-binding.

### Fix

Add a configuration flag to enforce HMAC, and plan to eventually make it mandatory.

```java
// InternalRequestVerifier.java:

private final boolean hmacRequired;

public InternalRequestVerifier(
        @Value("${internal.auth.shared-secret:}") String sharedSecret,
        @Value("${internal.auth.hmac-required:false}") boolean hmacRequired
) {
    this.sharedSecret = sharedSecret;
    this.hmacRequired = hmacRequired;
}

private void verifyHmacFromRequest() {
    var attrs = RequestContextHolder.getRequestAttributes();
    if (!(attrs instanceof ServletRequestAttributes sra)) {
        if (hmacRequired) {
            throw new UnauthorizedException("HMAC verification required but request context unavailable");
        }
        return;
    }
    HttpServletRequest request = sra.getRequest();
    String signature = request.getHeader("X-Internal-Signature");
    String timestampStr = request.getHeader("X-Internal-Timestamp");
    if (signature == null || timestampStr == null) {
        if (hmacRequired) {
            throw new UnauthorizedException("HMAC signature and timestamp headers are required");
        }
        return; // Graceful during migration
    }
    // ... rest of HMAC verification ...
}
```

---

## BUG-AC-003: Automated Access Expiration Leaves No Audit Trail

| Field | Value |
|-------|-------|
| **Severity** | **Medium** |
| **Category** | Data Integrity / Compliance |
| **File** | `service/AccessServiceImpl.java` |
| **Lines** | 970-1022 |

### Description

The `deactivateExpiredAccess()` method sets `active=false` on expired platform staff, vendor staff, and API keys. However, it **does not** record audit entries for these changes.

The platform/vendor staff CRUD operations (`createPlatformStaff`, `updatePlatformStaff`, `softDeletePlatformStaff`, `restorePlatformStaff`, and their vendor equivalents) all call `recordPlatformAudit()` / `recordVendorAudit()`. But the scheduler path skips this entirely:

```java
platformPage.forEach(staff -> {
    staff.setActive(false);               // State change...
    platformUserIds.add(staff.getKeycloakUserId());
});
platformStaffAccessRepository.saveAll(platformPage.getContent());
// No recordPlatformAudit() call!
```

**Impact**: An admin reviewing the audit log for a staff member would see `CREATED` and possibly `UPDATED` entries, but no record of the automated deactivation. It would appear the access is still active in the audit trail, creating a discrepancy between the audit log and actual state.

### Fix

```java
// AccessServiceImpl.java — in deactivateExpiredAccess(), after setting active=false:

platformPage.forEach(staff -> {
    staff.setActive(false);
    platformUserIds.add(staff.getKeycloakUserId());
    recordPlatformAudit(staff, AccessChangeAction.UPDATED, null, null, "Automated expiry");
});

// Similarly for vendor staff:
vendorPage.forEach(staff -> {
    staff.setActive(false);
    vendorUserIds.add(staff.getKeycloakUserId());
    recordVendorAudit(staff, AccessChangeAction.UPDATED, null, null, "Automated expiry");
});
```

Consider also adding an `EXPIRED` value to `AccessChangeAction` for clearer semantics:
```java
public enum AccessChangeAction {
    CREATED,
    UPDATED,
    SOFT_DELETED,
    RESTORED,
    EXPIRED  // New
}
```

---

## BUG-AC-004: Permission Group Scope Can Change After Staff Assignment

| Field | Value |
|-------|-------|
| **Severity** | **Medium** |
| **Category** | Data Integrity / Logic |
| **File** | `service/AccessServiceImpl.java` |
| **Lines** | 790-803 |

### Description

`updatePermissionGroup()` allows changing the `scope` field from PLATFORM to VENDOR (or vice versa) on an existing permission group. The `validatePermissionGroupScope()` check (line 506-518) runs during staff assignment (`applyPlatformStaff` / `applyVendorStaff`), but **not** when the group itself is updated.

**Scenario:**
1. Create permission group "Viewer" with scope=PLATFORM
2. Assign "Viewer" group to platform staff member X (`permissionGroupId` = Viewer's UUID)
3. Update "Viewer" group: change scope to VENDOR
4. Now platform staff member X has a VENDOR-scoped permission group assigned — semantically invalid

The `permissionGroupId` on staff records is a UUID column with no foreign key or scope check. No validation fires retroactively.

### Fix

Either prevent scope changes on groups that have active assignments, or remove the ability to change scope entirely (require delete + re-create).

```java
// AccessServiceImpl.java — updatePermissionGroup(), add before line 799:

if (entity.getScope() != request.scope()) {
    // Check if any staff records reference this group
    boolean hasPlatformAssignments = platformStaffAccessRepository
            .existsByPermissionGroupIdAndDeletedFalse(id);
    boolean hasVendorAssignments = vendorStaffAccessRepository
            .existsByPermissionGroupIdAndDeletedFalse(id);
    if (hasPlatformAssignments || hasVendorAssignments) {
        throw new ValidationException(
                "Cannot change scope of permission group that has active staff assignments. "
                + "Remove all assignments first or create a new group.");
    }
}
```

Add the repository methods:
```java
// PlatformStaffAccessRepository:
boolean existsByPermissionGroupIdAndDeletedFalse(UUID permissionGroupId);

// VendorStaffAccessRepository:
boolean existsByPermissionGroupIdAndDeletedFalse(UUID permissionGroupId);
```

---

## BUG-AC-005: Permission Group Deletion Without Active Assignment Check

| Field | Value |
|-------|-------|
| **Severity** | **Medium** |
| **Category** | Data Integrity / Logic |
| **File** | `service/AccessServiceImpl.java` |
| **Lines** | 806-813 |

### Description

```java
public void deletePermissionGroup(UUID id) {
    if (!permissionGroupRepository.existsById(id)) {
        throw new ResourceNotFoundException("Permission group not found: " + id);
    }
    permissionGroupRepository.deleteById(id);
}
```

A permission group can be hard-deleted even when staff records reference it via `permissionGroupId`. Since `permissionGroupId` is a plain UUID column (not a JPA `@ManyToOne` with foreign key), PostgreSQL won't enforce referential integrity, and the delete succeeds silently.

After deletion, staff records have a `permissionGroupId` pointing to a non-existent group. Any UI or API that tries to resolve the permission group name would fail or show stale data.

### Fix

Check for active assignments before deletion.

```java
// AccessServiceImpl.java — replace deletePermissionGroup():

@Override
@Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
public void deletePermissionGroup(UUID id) {
    if (!permissionGroupRepository.existsById(id)) {
        throw new ResourceNotFoundException("Permission group not found: " + id);
    }
    boolean hasPlatformAssignments = platformStaffAccessRepository
            .existsByPermissionGroupIdAndDeletedFalse(id);
    boolean hasVendorAssignments = vendorStaffAccessRepository
            .existsByPermissionGroupIdAndDeletedFalse(id);
    if (hasPlatformAssignments || hasVendorAssignments) {
        throw new ValidationException(
                "Cannot delete permission group with active staff assignments. "
                + "Reassign or remove staff from this group first.");
    }
    permissionGroupRepository.deleteById(id);
}
```

---

## BUG-AC-006: deactivateExpiredAccess() — Single Long Transaction for All Expiry Types

| Field | Value |
|-------|-------|
| **Severity** | **Medium** |
| **Category** | Architecture / Resilience |
| **File** | `service/AccessServiceImpl.java` |
| **Lines** | 970-1022 |

### Description

The `deactivateExpiredAccess()` method runs platform staff, vendor staff, AND API key expiry processing within a **single** `@Transactional` boundary with `timeout = 60` and `REPEATABLE_READ` isolation.

With REPEATABLE_READ in PostgreSQL, this holds a transaction snapshot for the entire duration. If there are thousands of expired records:
- The transaction snapshot prevents VACUUM from cleaning dead tuples in the affected tables
- Long-running REPEATABLE_READ transactions increase serialization conflict risk
- If any step fails (e.g., API key processing), the entire transaction rolls back, including already-processed platform and vendor staff deactivations

The method also accumulates all keycloakUserIds in memory (`platformUserIds`, `vendorUserIds`) for post-commit cache eviction. With thousands of expired records, this list grows unboundedly.

### Fix

Split into three independent transactions — one per entity type. Use `@Transactional(propagation = REQUIRES_NEW)` or extract each step into its own transactional method via a separate bean (to avoid proxy bypass).

```java
// Create a new class to avoid proxy self-invocation issues:

@Service
@RequiredArgsConstructor
public class AccessExpiryProcessor {

    private final PlatformStaffAccessRepository platformStaffAccessRepository;
    private final VendorStaffAccessRepository vendorStaffAccessRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final CacheManager cacheManager;

    @Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 30)
    public int deactivateExpiredPlatformStaff() {
        // Platform staff expiry logic + cache eviction
    }

    @Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 30)
    public int deactivateExpiredVendorStaff() {
        // Vendor staff expiry logic + cache eviction
    }

    @Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 30)
    public int deactivateExpiredApiKeys() {
        // API key expiry logic
    }
}
```

Update `AccessExpiryScheduler` to call each independently:
```java
@Scheduled(fixedDelayString = "${access.expiry.check-interval-ms:60000}")
public void deactivateExpired() {
    int count = 0;
    count += safeRun(() -> accessExpiryProcessor.deactivateExpiredPlatformStaff());
    count += safeRun(() -> accessExpiryProcessor.deactivateExpiredVendorStaff());
    count += safeRun(() -> accessExpiryProcessor.deactivateExpiredApiKeys());
    if (count > 0) {
        log.info("Scheduled expiry check deactivated {} records", count);
    }
}
```

Note: Changing to `READ_COMMITTED` is also appropriate for expiry processing since we don't need snapshot consistency — we just need to deactivate what's currently expired.

---

## BUG-AC-007: PermissionGroup Entity Missing Optimistic Locking

| Field | Value |
|-------|-------|
| **Severity** | **Low** |
| **Category** | Data Integrity / Concurrency |
| **File** | `entity/PermissionGroup.java` |

### Description

`PlatformStaffAccess` and `VendorStaffAccess` both have `@Version` fields for optimistic locking. The `GlobalExceptionHandler` already handles `OptimisticLockingFailureException` (line 57-61).

However, `PermissionGroup` has no `@Version` field. Two concurrent updates to the same permission group would result in a **lost update** — the last writer wins silently.

While permission groups are rarely updated and only by super_admin, the inconsistency with the other entities is a correctness gap.

### Fix

```java
// PermissionGroup.java — add:

@Version
private Long version;
```

---

## BUG-AC-008: Restore Sets active=true on Already-Expired Records

| Field | Value |
|-------|-------|
| **Severity** | **Low** |
| **Category** | Logic |
| **File** | `service/AccessServiceImpl.java` |
| **Lines** | 261-280 (platform), 411-432 (vendor) |

### Description

When restoring a soft-deleted staff record:
```java
entity.setDeleted(false);
entity.setDeletedAt(null);
entity.setActive(true);
```

The `active` flag is set to `true` unconditionally, without checking `accessExpiresAt`. If a staff member's access was set to expire at `2024-01-01`, was soft-deleted, and is then restored in 2026, the record becomes `active=true` with an expired `accessExpiresAt`.

The `getPlatformAccessByKeycloakUser()` lookup does check `isExpired()` at query time (line 288), so the effective access would be denied. But the DB state is misleading — `active=true` with a past expiry date. The scheduler would deactivate it again within 60 seconds, creating a brief inconsistency window.

### Fix

Check expiry before setting active on restore.

```java
// AccessServiceImpl.java — in restorePlatformStaff, replace line 269:

boolean shouldBeActive = !isExpired(entity.getAccessExpiresAt());
entity.setActive(shouldBeActive);

// Same for restoreVendorStaff
```

Alternatively, clear the expiry on restore if the intent is to grant indefinite access:
```java
entity.setActive(true);
entity.setAccessExpiresAt(null); // Clear expired date on restore
```

---

## BUG-AC-009: Invalid Pageable Sort Fields Return 500 Instead of 400

| Field | Value |
|-------|-------|
| **Severity** | **Low** |
| **Category** | Logic / Validation |
| **File** | `exception/GlobalExceptionHandler.java` |
| **Lines** | 63-67 |

### Description

All paginated endpoints accept `sort` query parameters from the client (e.g., `?sort=email,asc`). If the client sends an invalid sort field (`?sort=nonExistentField`), Spring Data throws `PropertyReferenceException`, which falls through to:

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<?> handleOther(Exception ex) {
    log.error("Unhandled access-service exception", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(error(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error"));
}
```

This returns `500 INTERNAL_SERVER_ERROR` with a full stack trace logged at ERROR level. It should return `400 BAD_REQUEST`.

### Fix

Add a specific handler for `PropertyReferenceException`:

```java
// GlobalExceptionHandler.java — add:

@ExceptionHandler(org.springframework.data.mapping.PropertyReferenceException.class)
public ResponseEntity<?> handlePropertyReference(
        org.springframework.data.mapping.PropertyReferenceException ex) {
    log.warn("Invalid sort property: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(error(HttpStatus.BAD_REQUEST, "Invalid sort field: " + ex.getPropertyName()));
}
```

---

## BUG-AC-010: LIKE Metacharacters in actorQuery Not Escaped

| Field | Value |
|-------|-------|
| **Severity** | **Low** |
| **Category** | Logic |
| **File** | `service/AccessServiceImpl.java` |
| **Lines** | 127-137 |

### Description

The audit search `actorQuery` parameter is embedded directly into a LIKE pattern:

```java
String like = "%" + normalizedActorQuery.toLowerCase(Locale.ROOT) + "%";
spec = spec.and((root, query, cb) -> cb.or(
        cb.like(cb.lower(cb.coalesce(root.get("actorSub"), "")), like),
        // ... 6 more LIKE clauses
));
```

LIKE metacharacters (`%`, `_`) in the user's search query are not escaped. This means:
- Searching for `50%` would match `50` followed by anything (treating `%` as a wildcard)
- Searching for `user_1` would match `user11`, `userA1`, etc. (treating `_` as single-char wildcard)

This is not a SQL injection (JPA parameterizes correctly) but a functional correctness issue affecting search precision.

### Fix

Escape LIKE metacharacters before building the pattern:

```java
// AccessServiceImpl.java — add helper method:

private String escapeLikePattern(String input) {
    return input.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
}

// Line 128 — replace:
String like = "%" + escapeLikePattern(normalizedActorQuery.toLowerCase(Locale.ROOT)) + "%";

// Update all cb.like() calls to include escape character:
cb.like(cb.lower(cb.coalesce(root.get("actorSub"), "")), like, '\\'),
```

---

## Audit Summary

| Severity | Count | IDs |
|----------|-------|-----|
| Critical | 0 | — |
| High | 1 | AC-001 |
| Medium | 5 | AC-002, AC-003, AC-004, AC-005, AC-006 |
| Low | 4 | AC-007, AC-008, AC-009, AC-010 |
| **Total** | **10** | |

## Positive Observations

The service demonstrates strong engineering practices in several areas:

- **Optimistic locking** on PlatformStaffAccess and VendorStaffAccess via `@Version`, with proper `OptimisticLockingFailureException` handling in GlobalExceptionHandler returning 409 Conflict.
- **Transaction isolation levels** are thoughtfully chosen: `READ_COMMITTED` for reads, `REPEATABLE_READ` for writes. All write methods specify explicit isolation and timeout.
- **Cache eviction after commit** using `TransactionSynchronizationManager.registerSynchronization()` prevents stale cache entries from failed transactions.
- **Targeted cache eviction** by keycloakUserId (not broad cache clears) for the frequently-accessed lookup caches.
- **Uniqueness validation** with both application-level checks (`existsBy...`) and database constraints (`@UniqueConstraint`), with `DataIntegrityViolationException` catch for race conditions.
- **Audit trail** for all manual CRUD operations on platform/vendor staff access (CREATED, UPDATED, SOFT_DELETED, RESTORED).
- **API key security**: SHA-256 hashing with `SecureRandom` 32-byte key generation; raw key returned only at creation time.
- **Input normalization** is thorough: `normalizeRequired()`, `normalizeEmail()`, `trimToNull()` applied consistently.
- **Pagination limits** enforced via `PaginationConfig` (max 100) and audit-specific `normalizeAuditSize()` (max 200).
- **Soft delete pattern** prevents permanent data loss and enables recovery.
- **Batch processing** in `deactivateExpiredAccess()` uses page-based iteration to avoid loading all records into memory at once.
- **Database indexing** is comprehensive: composite indexes for common query patterns (active + deleted + expiry, vendor + keycloak user).
