# QA Bugs & Tasks — customer-service

> **Auditor**: Principal Backend QA Architect & Senior Security Bug Hunter
> **Service**: `customer-service` (port 8081)
> **Date**: 2026-02-27
> **Findings**: 4 total — 0 Critical, 1 High, 2 Medium, 1 Low

---

## Domain Map

| Layer | Component | Key Responsibility |
|---|---|---|
| Controller | `CustomerController` (`/customers`) | 20 customer-facing + internal endpoints (CRUD, addresses, loyalty, comm prefs, activity log, linked accounts) |
| Controller | `InternalCustomerController` (`/internal/customers`) | Service-to-service customer lookup by keycloakId |
| Controller | `InternalCustomerAnalyticsController` (`/internal/customers/analytics`) | Platform analytics + customer profile summary |
| Service | `CustomerServiceImpl` | Core customer logic — registration, profile, addresses, loyalty, comm prefs |
| Service | `CustomerAnalyticsService` | Aggregates platform/customer analytics from repos |
| Auth | `KeycloakManagementService` | Keycloak admin client — user creation, lookup, enable/disable, name updates (CB + Retry) |
| Repository | `CustomerRepository` | Customer CRUD, email/keycloakId lookup, analytics queries |
| Repository | `CustomerAddressRepository` | Address CRUD with soft-delete |
| Repository | `CommunicationPreferencesRepository` | Communication preferences CRUD |
| Repository | `CustomerActivityLogRepository` | Activity log queries |
| Entity | `Customer` | `@Version`, unique email + keycloakId, loyalty tier/points, active flag |
| Entity | `CustomerAddress` | `@Version`, `@ManyToOne` to Customer, soft-delete, default shipping/billing |
| Entity | `CommunicationPreferences` | `@OneToOne` to Customer, notification preferences |
| Entity | `CustomerActivityLog` | Audit trail — action, details, IP address |
| Filter | `CustomerMutationIdempotencyFilter` | Redis-backed idempotency for POST/PUT/DELETE mutations |
| Security | `InternalRequestVerifier` | Shared secret + optional HMAC verification |

---

## BUG-CUST-001 — Keycloak Side-Effect Executes Before DB Commit in deactivateAccount and updateProfile

| Field | Value |
|---|---|
| **Severity** | **HIGH** |
| **Category** | Data Integrity & Consistency |
| **Affected Files** | `service/CustomerServiceImpl.java` |
| **Lines** | 178–205, 207–230 |

### Description

Both `deactivateAccount()` and `updateProfile()` execute an **irreversible Keycloak side-effect** (disable user / update names) **before** the DB transaction commits. If the subsequent DB transaction fails (connection timeout, optimistic lock, constraint violation), the Keycloak state is already changed with no rollback:

1. **`deactivateAccount()`** — Keycloak user is disabled (line 220) before the DB sets `active = false` (line 225). If the DB transaction fails, the user **cannot log in** (Keycloak disabled) but the system still considers them active. Other services querying the customer record see `active = true`, leading to inconsistent authorization decisions.

2. **`updateProfile()`** — Keycloak names are updated (line 190) before the DB stores the new name (line 196). If the DB fails, the user's display name in the JWT (from Keycloak) differs from the name returned by customer-service endpoints.

### Current Code

**`CustomerServiceImpl.java:207–230`** — `deactivateAccount()`:
```java
@CachePut(cacheNames = "customerByKeycloak", key = "#keycloakId == null ? '' : #keycloakId.trim()")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public CustomerResponse deactivateAccount(String keycloakId) {
    String normalizedKeycloakId = normalizeKeycloakId(keycloakId);

    Customer customer = customerRepository.findByKeycloakId(normalizedKeycloakId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer not found for keycloak id"));

    if (!customer.isActive()) {
        return toResponse(customer);
    }

    keycloakManagementService.setUserEnabled(normalizedKeycloakId, false); // ← Keycloak disabled FIRST

    return transactionTemplate.execute(status -> {                         // ← DB update SECOND
        Customer managed = customerRepository.findByKeycloakId(normalizedKeycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found for keycloak id"));
        managed.setActive(false);
        managed.setDeactivatedAt(Instant.now());
        Customer saved = customerRepository.save(managed);
        return toResponse(saved);
    });
}
```

**`CustomerServiceImpl.java:178–205`** — `updateProfile()`:
```java
@CachePut(cacheNames = "customerByKeycloak", key = "#keycloakId == null ? '' : #keycloakId.trim()")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public CustomerResponse updateProfile(String keycloakId, UpdateCustomerProfileRequest request, String ipAddress) {
    String normalizedKeycloakId = normalizeKeycloakId(keycloakId);
    Customer customer = findActiveCustomerByKeycloakId(normalizedKeycloakId);

    String firstName = request.firstName().trim();
    String lastName = request.lastName().trim();
    String fullName = (firstName + " " + lastName).trim();
    String customerDbKeycloakId = customer.getKeycloakId();

    if (StringUtils.hasText(customerDbKeycloakId)) {
        keycloakManagementService.updateUserNames(customerDbKeycloakId, firstName, lastName); // ← Keycloak FIRST
    }

    return transactionTemplate.execute(status -> {  // ← DB SECOND
        Customer managed = customerRepository.findByKeycloakId(normalizedKeycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found for keycloak id"));
        managed.setName(fullName);
        // ...
        Customer saved = customerRepository.save(managed);
        logActivity(saved.getId(), "PROFILE_UPDATE", "Profile updated", ipAddress);
        return toResponse(saved);
    });
}
```

### Fix

Reverse the order: DB transaction first (reversible), then Keycloak (harder to reverse). If Keycloak fails after DB commit, throw to prevent `@CachePut` from caching stale data — the DB is the source of truth and the customer-service's `findActiveCustomerByKeycloakId()` enforces the deactivated state regardless of Keycloak.

**Step 1** — Fix `deactivateAccount()`. Replace `CustomerServiceImpl.java:207–230`:

```java
@CachePut(cacheNames = "customerByKeycloak", key = "#keycloakId == null ? '' : #keycloakId.trim()")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public CustomerResponse deactivateAccount(String keycloakId) {
    String normalizedKeycloakId = normalizeKeycloakId(keycloakId);

    Customer customer = customerRepository.findByKeycloakId(normalizedKeycloakId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer not found for keycloak id"));

    if (!customer.isActive()) {
        return toResponse(customer);
    }

    CustomerResponse result = transactionTemplate.execute(status -> {
        Customer managed = customerRepository.findByKeycloakId(normalizedKeycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found for keycloak id"));
        managed.setActive(false);
        managed.setDeactivatedAt(Instant.now());
        Customer saved = customerRepository.save(managed);
        return toResponse(saved);
    });

    keycloakManagementService.setUserEnabled(normalizedKeycloakId, false);

    return result;
}
```

**Step 2** — Fix `updateProfile()`. Replace `CustomerServiceImpl.java:178–205`:

```java
@CachePut(cacheNames = "customerByKeycloak", key = "#keycloakId == null ? '' : #keycloakId.trim()")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public CustomerResponse updateProfile(String keycloakId, UpdateCustomerProfileRequest request, String ipAddress) {
    String normalizedKeycloakId = normalizeKeycloakId(keycloakId);
    Customer customer = findActiveCustomerByKeycloakId(normalizedKeycloakId);

    String firstName = request.firstName().trim();
    String lastName = request.lastName().trim();
    String fullName = (firstName + " " + lastName).trim();
    String customerDbKeycloakId = customer.getKeycloakId();

    CustomerResponse result = transactionTemplate.execute(status -> {
        Customer managed = customerRepository.findByKeycloakId(normalizedKeycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found for keycloak id"));
        managed.setName(fullName);
        managed.setPhone(trimToNull(request.phone()));
        managed.setAvatarUrl(trimToNull(request.avatarUrl()));
        managed.setDateOfBirth(request.dateOfBirth());
        managed.setGender(request.gender());
        Customer saved = customerRepository.save(managed);
        logActivity(saved.getId(), "PROFILE_UPDATE", "Profile updated", ipAddress);
        return toResponse(saved);
    });

    if (StringUtils.hasText(customerDbKeycloakId)) {
        keycloakManagementService.updateUserNames(customerDbKeycloakId, firstName, lastName);
    }

    return result;
}
```

---

## BUG-CUST-002 — register() and registerIdentity() Race Condition Surfaces as 500 Error

| Field | Value |
|---|---|
| **Severity** | **Medium** |
| **Category** | Data Integrity & Concurrency |
| **Affected Files** | `service/CustomerServiceImpl.java` |
| **Lines** | 99–126, 128–174 |

### Description

Both `register()` and `registerIdentity()` follow a **check-then-act** pattern: verify email/keycloakId uniqueness, then insert. Under concurrent requests, the check passes for both threads but the second insert violates the database unique constraint.

**`register()`** (line 99): Uses `Propagation.NOT_SUPPORTED` — the `existsByEmail` check (line 104) and the `transactionTemplate.execute()` insert (line 116) are in separate transactions. Two concurrent registrations with the same email both pass the check, both create (or resolve) the Keycloak user, but the second DB insert throws `DataIntegrityViolationException`. This exception is **not caught** by any handler except the generic `Exception` handler, returning a **500 "Unexpected error"** instead of a friendly **409 "Customer already exists"**.

**`registerIdentity()`** (line 128): Uses `REPEATABLE_READ`, but PostgreSQL's MVCC allows two concurrent transactions to both see no existing record. The second insert throws `DataIntegrityViolationException` on the `uk_customers_keycloak_id` unique constraint — also unhandled.

The idempotency filter partially mitigates this (same key → cached response), but does not protect against different idempotency keys or the `register` endpoint where there is no `X-User-Sub` for actor scoping.

### Current Code

**`CustomerServiceImpl.java:99–126`** — `register()`:
```java
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public CustomerResponse register(RegisterCustomerRequest request) {
    String email = request.email().trim().toLowerCase();

    if (customerRepository.existsByEmail(email)) {
        throw new DuplicateResourceException("Customer already exists with email: " + email);
    }

    String keycloakId;
    try {
        keycloakId = keycloakManagementService.createUser(email, request.password(), request.name().trim());
    } catch (KeycloakUserExistsException ex) {
        keycloakId = keycloakManagementService.getUserIdByEmail(email);
    }
    final String resolvedKeycloakId = keycloakId;

    return transactionTemplate.execute(status -> {
        Customer saved = customerRepository.save(
                Customer.builder()
                        .name(request.name().trim())
                        .email(email)
                        .keycloakId(resolvedKeycloakId)
                        .build()
        );
        return toResponse(saved);
    });
}
```

### Fix

Catch `DataIntegrityViolationException` inside the `transactionTemplate.execute()` callback and convert it to a `DuplicateResourceException`.

**Step 1** — Add the import at the top of `CustomerServiceImpl.java`:

```java
import org.springframework.dao.DataIntegrityViolationException;
```

**Step 2** — Fix `register()`. Replace `CustomerServiceImpl.java:116–125`:

```java
    return transactionTemplate.execute(status -> {
        try {
            Customer saved = customerRepository.save(
                    Customer.builder()
                            .name(request.name().trim())
                            .email(email)
                            .keycloakId(resolvedKeycloakId)
                            .build()
            );
            return toResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException("Customer already exists with email: " + email);
        }
    });
```

**Step 3** — Fix `registerIdentity()`. Wrap the save in `registerIdentity()` to handle the race. Replace `CustomerServiceImpl.java:165–173`:

```java
    try {
        Customer saved = customerRepository.save(
                Customer.builder()
                        .name(name)
                        .email(normalizedEmail)
                        .keycloakId(normalizedKeycloakId)
                        .build()
        );
        return toResponse(saved);
    } catch (DataIntegrityViolationException ex) {
        Customer existingRetry = customerRepository.findByKeycloakId(normalizedKeycloakId).orElse(null);
        if (existingRetry != null) {
            return toResponse(existingRetry);
        }
        throw new DuplicateResourceException("Customer already exists with email: " + normalizedEmail);
    }
```

---

## BUG-CUST-003 — addLoyaltyPoints() Lost Update Under Concurrent Access

| Field | Value |
|---|---|
| **Severity** | **Medium** |
| **Category** | Data Integrity & Concurrency |
| **Affected Files** | `service/CustomerServiceImpl.java`, `repo/CustomerRepository.java` |
| **Lines** | `CustomerServiceImpl.java:492–503` |

### Description

`addLoyaltyPoints()` performs a **read-modify-write** on the customer's `loyaltyPoints`:

```java
customer.setLoyaltyPoints(customer.getLoyaltyPoints() + points);
```

With `@Version` optimistic locking and `REPEATABLE_READ`, two concurrent calls (e.g., two orders completing simultaneously for the same customer) cause:

1. Thread A reads customer (100 points, version 1)
2. Thread B reads customer (100 points, version 1)
3. Thread A saves (150 points, version 2) — success
4. Thread B saves (130 points, version 2) — `ObjectOptimisticLockingFailureException`

Thread B's points are **permanently lost**. The exception is unhandled and surfaces as a **500 error** to the calling service (order-service). There is no retry logic.

### Current Code

**`CustomerServiceImpl.java:492–503`**:
```java
@CachePut(cacheNames = "customerByKeycloak", key = "#result.keycloakId()")
@Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)
public CustomerResponse addLoyaltyPoints(UUID customerId, int points) {
    Customer customer = customerRepository.findById(customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + customerId));

    customer.setLoyaltyPoints(customer.getLoyaltyPoints() + points);
    customer.setLoyaltyTier(calculateTier(customer.getLoyaltyPoints()));
    Customer saved = customerRepository.save(customer);
    return toResponse(saved);
}
```

### Fix

Use a native atomic SQL increment to eliminate the read-modify-write race entirely, then re-read the customer for the response and tier recalculation.

**Step 1** — Add an atomic increment query to `CustomerRepository.java`. Add after the existing methods:

```java
@Modifying
@Query("UPDATE Customer c SET c.loyaltyPoints = c.loyaltyPoints + :points, c.version = c.version + 1 WHERE c.id = :id")
int addLoyaltyPointsAtomically(@Param("id") UUID id, @Param("points") int points);
```

**Step 2** — Rewrite `addLoyaltyPoints()` in `CustomerServiceImpl.java:492–503`:

```java
@CachePut(cacheNames = "customerByKeycloak", key = "#result.keycloakId()")
@Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 20)
public CustomerResponse addLoyaltyPoints(UUID customerId, int points) {
    int updated = customerRepository.addLoyaltyPointsAtomically(customerId, points);
    if (updated == 0) {
        throw new ResourceNotFoundException("Customer not found: " + customerId);
    }

    Customer customer = customerRepository.findById(customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + customerId));

    CustomerLoyaltyTier newTier = calculateTier(customer.getLoyaltyPoints());
    if (customer.getLoyaltyTier() != newTier) {
        customer.setLoyaltyTier(newTier);
        customer = customerRepository.save(customer);
    }

    return toResponse(customer);
}
```

**Note**: The isolation is relaxed to `READ_COMMITTED` because the atomic increment eliminates the need for `REPEATABLE_READ`. The tier recalculation re-reads the post-increment value, so it's always based on the latest points.

---

## BUG-CUST-004 — getCommunicationPreferences Race Condition on Default Creation

| Field | Value |
|---|---|
| **Severity** | **Low** |
| **Category** | Data Integrity & Concurrency |
| **Affected Files** | `service/CustomerServiceImpl.java` |
| **Lines** | 518–524, 559–564 |

### Description

`getCommunicationPreferences()` auto-creates a default `CommunicationPreferences` record if none exists (via `getDefaultCommunicationPreferences()`). The `communication_preferences` table has a unique constraint on `customer_id` (`uk_comm_prefs_customer_id`).

If two concurrent GET requests arrive for the same customer who has no preferences yet, both find no existing record and both attempt to insert. The second insert violates the unique constraint, throwing an unhandled `DataIntegrityViolationException` → **500 error**.

The same pattern exists in `updateCommunicationPreferences()` (line 531–537), which also auto-creates preferences on first update.

### Current Code

**`CustomerServiceImpl.java:518–524`** — `getCommunicationPreferences()`:
```java
@Transactional(readOnly = false, isolation = Isolation.READ_COMMITTED, timeout = 10)
public CommunicationPreferencesResponse getCommunicationPreferences(String keycloakId) {
    Customer customer = findActiveCustomerByKeycloakId(keycloakId);
    CommunicationPreferences prefs = communicationPreferencesRepository.findByCustomerId(customer.getId())
            .orElseGet(() -> getDefaultCommunicationPreferences(customer));
    return toCommPrefsResponse(prefs);
}
```

**`CustomerServiceImpl.java:559–564`** — `getDefaultCommunicationPreferences()`:
```java
private CommunicationPreferences getDefaultCommunicationPreferences(Customer customer) {
    CommunicationPreferences prefs = CommunicationPreferences.builder()
            .customer(customer)
            .build();
    return communicationPreferencesRepository.save(prefs);
}
```

### Fix

Catch `DataIntegrityViolationException` in the default creation and re-read from the database.

Replace `CustomerServiceImpl.java:559–564`:

```java
private CommunicationPreferences getDefaultCommunicationPreferences(Customer customer) {
    try {
        CommunicationPreferences prefs = CommunicationPreferences.builder()
                .customer(customer)
                .build();
        return communicationPreferencesRepository.save(prefs);
    } catch (org.springframework.dao.DataIntegrityViolationException ex) {
        return communicationPreferencesRepository.findByCustomerId(customer.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Communication preferences not found"));
    }
}
```

---

## Summary

| ID | Severity | Category | Title |
|---|---|---|---|
| BUG-CUST-001 | **HIGH** | Data Integrity & Consistency | Keycloak side-effect before DB commit causes inconsistent state on failure |
| BUG-CUST-002 | Medium | Data Integrity & Concurrency | register() and registerIdentity() race condition surfaces as 500 error |
| BUG-CUST-003 | Medium | Data Integrity & Concurrency | addLoyaltyPoints() lost update under concurrent access |
| BUG-CUST-004 | Low | Data Integrity & Concurrency | getCommunicationPreferences race condition on default creation |
