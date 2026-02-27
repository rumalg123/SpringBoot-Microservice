# QA Bugs & Tasks — product-service

> **Auditor**: Principal Backend QA Architect & Senior Security Bug Hunter
> **Service**: `product-service` (port 8084)
> **Date**: 2026-02-27
> **Findings**: 4 total — 1 Critical, 1 High, 1 Medium, 1 Low

---

## Domain Map

| Layer | Component | Key Responsibility |
|---|---|---|
| Controller | `ProductController` (`/products`) | 6 public endpoints: list, getByIdOrSlug, variations, recordView, getImage, slugAvailable |
| Controller | `AdminProductController` (`/admin/products`) | 17 admin endpoints: CRUD, variations, bulk ops (delete/price/category), approve/reject workflow, CSV export/import, image upload |
| Controller | `CategoryController` (`/categories`) | 3 public endpoints: list, paged list, slugAvailable |
| Controller | `AdminCategoryController` (`/admin/categories`) | 7 admin endpoints: CRUD (create/update/delete/restore), list active/deleted |
| Controller | `InternalProductSearchController` (`/internal/products/search`) | Catalog page, updated-since for search-service |
| Controller | `InternalProductAnalyticsController` (`/internal/products/analytics`) | Platform/vendor analytics summaries |
| Controller | `InternalProductPersonalizationController` (`/internal/products/personalization`) | Batch product summaries for personalization-service |
| Controller | `InternalProductMaintenanceController` (`/internal/products`) | Vendor cache eviction, vendor-wide deactivation |
| Service | `ProductServiceImpl` | Core product logic — CQRS with read model, caching (Redis), approval workflow, bulk operations, CSV import/export, vendor visibility, stock enrichment |
| Service | `CategoryServiceImpl` | Category CRUD with tree validation, max depth enforcement, cached lists |
| Service | `AdminProductAccessScopeService` | RBAC: resolves actor scope (super_admin, platform_staff, vendor_admin, vendor_staff) via access-service + vendor-service |
| Service | `ProductAnalyticsService` | Read-only analytics aggregate queries |
| Service | `ProductCacheVersionService` | Redis-backed cache version bumping for invalidation |
| Service | `ProductCatalogReadModelProjector` | Maintains `ProductCatalogRead` denormalized read model |
| Repository | `ProductRepository` | JpaSpecificationExecutor, `@EntityGraph` detail fetches, `@Modifying` counters, analytics queries |
| Repository | `ProductCatalogReadRepository` | JpaSpecificationExecutor for storefront search |
| Repository | `CategoryRepository` | Paginated/non-paginated finders, child existence checks |
| Repository | `CategoryAttributeRepository` | Batch fetch by category IDs |
| Repository | `ProductSpecificationRepository` | Per-product spec management |
| Entity | `Product` | `@Version`, vendorId, approvalStatus, parentProductId, `@ManyToMany` categories, `@ElementCollection` images/variations/bundledProductIds, soft delete |
| Entity | `Category` | `@Version`, tree (parentCategoryId/depth/path), normalizedName unique, soft delete |
| Entity | `ProductCatalogRead` | Denormalized read model with lowercased fields, category tokens, vendor name |
| Client | `AccessClient` | Platform/vendor staff permission lookup (CB: accessService) |
| Client | `VendorAccessClient` | Vendor admin membership lookup (CB: vendorService) |
| Client | `VendorOperationalStateClient` | Vendor verified/visible state (CB: vendorService) |
| Client | `InventoryClient` | Stock availability enrichment (CB: inventoryService) |
| Filter | `ProductMutationIdempotencyFilter` | Redis-backed idempotency for admin POST/PUT/DELETE mutations |
| Config | `CacheConfig` | Redis-backed caching with per-cache TTLs, SerializationException error handler |
| Config | `ProductCatalogReadModelBootstrap` | Delayed read model rebuild on startup |

---

## BUG-PROD-001 — Bulk Operations Skip Vendor Tenant Isolation

| Field | Value |
|---|---|
| **Severity** | **CRITICAL** |
| **Category** | Security & Access Control |
| **Affected Files** | `controller/AdminProductController.java`, `service/ProductServiceImpl.java` |
| **Lines** | `AdminProductController.java:242–276`, `ProductServiceImpl.java:499–606` |

### Description

The three bulk operation endpoints (`bulkDelete`, `bulkPriceUpdate`, `bulkCategoryReassign`) only call `assertCanManageProductOperations()`, which verifies the caller has **some** product management access (including vendor-scoped). They do **not** resolve the caller's vendor scope or validate per-product ownership.

A vendor admin with `vendor.products.manage` on vendor A can:
1. **Bulk-delete** competitor vendor B's products by passing their product IDs.
2. **Bulk-update pricing** of any vendor's products to arbitrary values.
3. **Bulk-reassign categories** of any vendor's products.

This is a complete tenant isolation breach with data destruction capability.

By contrast, the regular admin list endpoints (`listActive`, `listDeleted`) correctly call `resolveScopedVendorFilter()` to restrict vendor-scoped actors — the bulk endpoints simply skip this check.

### Current Code

**`AdminProductController.java:242–252`** — bulkDelete has no vendor scope:
```java
@PostMapping("/bulk-delete")
public BulkOperationResult bulkDelete(
        @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
        @RequestHeader(value = "X-User-Sub", required = false) String userSub,
        @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
        @Valid @RequestBody BulkDeleteRequest request
) {
    internalRequestVerifier.verify(internalAuth);
    adminProductAccessScopeService.assertCanManageProductOperations(userSub, userRoles, internalAuth);
    return productService.bulkDelete(request);
}
```

**`AdminProductController.java:254–264`** — bulkPriceUpdate, same pattern:
```java
adminProductAccessScopeService.assertCanManageProductOperations(userSub, userRoles, internalAuth);
return productService.bulkPriceUpdate(request);
```

**`AdminProductController.java:266–276`** — bulkCategoryReassign, same pattern:
```java
adminProductAccessScopeService.assertCanManageProductOperations(userSub, userRoles, internalAuth);
return productService.bulkCategoryReassign(request);
```

**`ProductServiceImpl.java:503–516`** — bulkDelete processes any product without vendor check:
```java
for (UUID id : ids) {
    try {
        Product product = productRepository.findById(id)
                .filter(p -> !p.isDeleted())
                .orElse(null);
        if (product == null) {
            errors.add("Product not found or already deleted: " + id);
            continue;
        }
        product.setDeleted(true);
        // ...
```

### Fix

**Step 1** — Add a vendor-scope-aware method to `AdminProductAccessScopeService` that returns the set of allowed vendor IDs (or null for platform-level actors):

**`AdminProductAccessScopeService.java`** — Add after `assertCanManageProductOperations` (~line 106):
```java
public Set<UUID> resolveAllowedVendorIds(String userSub, String userRolesHeader, String internalAuth) {
    ActorScope scope = resolveScope(userSub, userRolesHeader, internalAuth);
    if (scope.superAdmin() || scope.platformProductsManage()) {
        return null; // null = all vendors allowed
    }
    if (scope.vendorProductVendorIds().isEmpty()) {
        throw new UnauthorizedException("Caller does not have product management access");
    }
    return scope.vendorProductVendorIds();
}
```

**Step 2** — In each controller bulk endpoint, resolve the allowed vendor IDs and pass them to the service. Replace `AdminProductController.java:242–252`:
```java
@PostMapping("/bulk-delete")
public BulkOperationResult bulkDelete(
        @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
        @RequestHeader(value = "X-User-Sub", required = false) String userSub,
        @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
        @Valid @RequestBody BulkDeleteRequest request
) {
    internalRequestVerifier.verify(internalAuth);
    Set<UUID> allowedVendorIds = adminProductAccessScopeService.resolveAllowedVendorIds(userSub, userRoles, internalAuth);
    return productService.bulkDelete(request, allowedVendorIds);
}
```

Apply the same change to `bulkPriceUpdate` and `bulkCategoryReassign`.

**Step 3** — Add vendor ownership validation in each service bulk method. In `ProductServiceImpl.java`, update `bulkDelete` (replace lines 503–510):
```java
for (UUID id : ids) {
    try {
        Product product = productRepository.findById(id)
                .filter(p -> !p.isDeleted())
                .orElse(null);
        if (product == null) {
            errors.add("Product not found or already deleted: " + id);
            continue;
        }
        if (allowedVendorIds != null && !allowedVendorIds.contains(product.getVendorId())) {
            errors.add("Access denied for product: " + id);
            continue;
        }
        product.setDeleted(true);
```

Apply the same vendor check to `bulkPriceUpdate` (after line 541) and `bulkCategoryReassign` (after line 588).

---

## BUG-PROD-002 — Vendor Admin Can Self-Approve/Reject Products and Export Full Catalog

| Field | Value |
|---|---|
| **Severity** | **HIGH** |
| **Category** | Security & Access Control |
| **Affected Files** | `controller/AdminProductController.java`, `service/AdminProductAccessScopeService.java` |
| **Lines** | `AdminProductController.java:217–240, 278–304` |

### Description

Three sets of admin endpoints use `assertCanManageProductOperations()` where they should require platform-level access:

**1. Approve/Reject** — The approval workflow is designed so vendors create products (DRAFT → PENDING_REVIEW) and **platform admins** review them. However, `approve()` and `reject()` only call `assertCanManageProductOperations()`, which permits vendor admins. A vendor admin can:
- Approve their own products without platform review
- Approve/reject **any** vendor's products (no per-product ownership check)

**2. CSV Export** — `exportCsv()` exports **all** products across all vendors. A vendor admin calling this endpoint receives the entire product catalog including competitors' names, descriptions, pricing, SKUs, and vendor IDs.

**3. CSV Import** — `importCsv()` creates products using vendor IDs from the CSV data without validating against the caller's vendor scope. A vendor admin can inject products under other vendors.

### Current Code

**`AdminProductController.java:217–227`** — approve allows vendor admins:
```java
@PostMapping("/{id}/approve")
public ProductResponse approve(
        @RequestHeader(value = "X-Internal-Auth", required = false) String internalAuth,
        @RequestHeader(value = "X-User-Sub", required = false) String userSub,
        @RequestHeader(value = "X-User-Roles", required = false) String userRoles,
        @PathVariable UUID id
) {
    internalRequestVerifier.verify(internalAuth);
    adminProductAccessScopeService.assertCanManageProductOperations(userSub, userRoles, internalAuth);
    return productService.approveProduct(id);
}
```

**`AdminProductController.java:229–240`** — reject has the same issue.

**`AdminProductController.java:278–292`** — export leaks all vendor data:
```java
adminProductAccessScopeService.assertCanManageProductOperations(userSub, userRoles, internalAuth);
byte[] csvBytes = productService.exportProductsCsv();
```

**`AdminProductController.java:294–304`** — import bypasses vendor scope:
```java
adminProductAccessScopeService.assertCanManageProductOperations(userSub, userRoles, internalAuth);
return productService.importProductsCsv(file.getInputStream());
```

### Fix

**Step 1** — Add a platform-level-only assertion to `AdminProductAccessScopeService`. Add after `assertCanManageCategories` (~line 114):

```java
public void assertPlatformLevelProductManagement(String userSub, String userRolesHeader, String internalAuth) {
    ActorScope scope = resolveScope(userSub, userRolesHeader, internalAuth);
    if (scope.superAdmin() || scope.platformProductsManage()) {
        return;
    }
    throw new UnauthorizedException("Only platform-level admins can perform this operation");
}
```

**Step 2** — Replace the auth check in approve/reject/export/import endpoints.

Replace `AdminProductController.java:225`:
```java
adminProductAccessScopeService.assertPlatformLevelProductManagement(userSub, userRoles, internalAuth);
```

Replace `AdminProductController.java:238`:
```java
adminProductAccessScopeService.assertPlatformLevelProductManagement(userSub, userRoles, internalAuth);
```

Replace `AdminProductController.java:286`:
```java
adminProductAccessScopeService.assertPlatformLevelProductManagement(userSub, userRoles, internalAuth);
```

Replace `AdminProductController.java:301`:
```java
adminProductAccessScopeService.assertPlatformLevelProductManagement(userSub, userRoles, internalAuth);
```

---

## BUG-PROD-003 — Resilience4j Retries Broken for AccessClient, VendorAccessClient, VendorOperationalStateClient

| Field | Value |
|---|---|
| **Severity** | **Medium** |
| **Category** | Architecture & Resilience |
| **Affected Files** | `client/AccessClient.java`, `client/VendorAccessClient.java`, `client/VendorOperationalStateClient.java`, `application.yaml` |
| **Lines** | `AccessClient.java:41–45`, `VendorAccessClient.java:40–46`, `VendorOperationalStateClient.java:49–53`, `application.yaml:86–113` |

### Description

The Resilience4j retry configuration specifies retryable exceptions:

```yaml
retryExceptions:
  - java.io.IOException
  - org.springframework.web.client.ResourceAccessException
  - org.springframework.web.client.HttpServerErrorException
```

However, three of four clients catch `RestClientException` (parent of both `ResourceAccessException` and `HttpServerErrorException`) and **re-wrap** it as `ServiceUnavailableException` **before** the exception reaches the Retry AOP aspect:

```java
// AccessClient.java:43–45
} catch (RestClientException | IllegalStateException ex) {
    throw new ServiceUnavailableException("Access service unavailable for platform lookup", ex);
}
```

Since `ServiceUnavailableException` is **not** in the `retryExceptions` list, the Retry aspect sees it and does **not** retry. All three clients have identical patterns:
- `AccessClient.getPlatformAccessByKeycloakUser` and `listVendorStaffAccessByKeycloakUser`
- `VendorAccessClient.listAccessibleVendorsByKeycloakUser`
- `VendorOperationalStateClient.getState` and `getStates`

**Note**: `InventoryClient` does **not** have this issue — it `throw ex` (re-throws the original `RestClientException`), so retries work correctly for it.

Transient connectivity failures and 5xx responses from access-service and vendor-service immediately surface as errors with zero retries, directly impacting RBAC resolution and vendor visibility checks.

### Fix

Add `ServiceUnavailableException` to the retry configuration for the affected instances.

**`application.yaml`** — Replace the retry section for `vendorService` (lines 87–95):
```yaml
      vendorService:
        maxAttempts: 3
        waitDuration: 300ms
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
        retryExceptions:
          - java.io.IOException
          - org.springframework.web.client.ResourceAccessException
          - org.springframework.web.client.HttpServerErrorException
          - com.rumal.product_service.exception.ServiceUnavailableException
```

Apply the same change to `accessService` (lines 96–104) and `inventoryService` (lines 105–113).

Adding it to `inventoryService` is a no-op since that client already re-throws the original exception, but it provides a safety net.

---

## BUG-PROD-004 — `approveAllDraft` Repository Method Exposed Without Any Controller or Guard

| Field | Value |
|---|---|
| **Severity** | **Low** |
| **Category** | Data Integrity & Concurrency |
| **Affected Files** | `repo/ProductRepository.java`, `repo/ProductCatalogReadRepository.java` |
| **Lines** | `ProductRepository.java:53–54`, `ProductCatalogReadRepository.java:21–22` |

### Description

`ProductRepository` exposes a `@Modifying` method that mass-approves every DRAFT product in the system:

```java
@Modifying
@Query("update Product p set p.approvalStatus = ... APPROVED where p.approvalStatus = ... DRAFT")
int approveAllDraft();
```

`ProductCatalogReadRepository` has a similar `bulkUpdateApprovalStatus` method. Neither method is called from any service or controller in the current codebase, but both are public `@Modifying` JPQL queries that bypass the per-product approval workflow (which requires PENDING_REVIEW status first).

If either method is ever invoked — intentionally or accidentally (e.g., by a future feature, a data migration script, or a test-runner side effect) — it would:
1. Approve **every** draft product across **all** vendors without review.
2. Skip the DRAFT → PENDING_REVIEW → APPROVED state machine.
3. Bypass the vendor verification check (`assertVendorVerified`).
4. Not update the read model, causing the write table and read table to be out of sync.

### Current Code

**`ProductRepository.java:53–54`**:
```java
@Modifying
@Query("update Product p set p.approvalStatus = com.rumal.product_service.entity.ApprovalStatus.APPROVED where p.approvalStatus = com.rumal.product_service.entity.ApprovalStatus.DRAFT")
int approveAllDraft();
```

**`ProductCatalogReadRepository.java:21–22`**:
```java
@Modifying
@Query("update ProductCatalogRead r set r.approvalStatus = :to where r.approvalStatus = :from")
int bulkUpdateApprovalStatus(@Param("from") ApprovalStatus from, @Param("to") ApprovalStatus to);
```

### Fix

Remove the unused `approveAllDraft()` method from `ProductRepository`, since it bypasses the approval state machine and has no legitimate use case.

**`ProductRepository.java`** — Delete lines 52–54:
```java
// DELETE THIS METHOD:
@Modifying
@Query("update Product p set p.approvalStatus = com.rumal.product_service.entity.ApprovalStatus.APPROVED where p.approvalStatus = com.rumal.product_service.entity.ApprovalStatus.DRAFT")
int approveAllDraft();
```

The `bulkUpdateApprovalStatus` method on `ProductCatalogReadRepository` is more general-purpose and could be useful for read model maintenance, so it can remain but should be documented as "must always be called alongside a corresponding write-side update."

---

## Summary

| ID | Severity | Category | Title |
|---|---|---|---|
| BUG-PROD-001 | **CRITICAL** | Security & Access Control | Bulk operations skip vendor tenant isolation — vendor admin can delete/modify any vendor's products |
| BUG-PROD-002 | **HIGH** | Security & Access Control | Vendor admin can self-approve products, export full catalog, import under other vendors |
| BUG-PROD-003 | Medium | Architecture & Resilience | Resilience4j retries broken for 3 of 4 inter-service clients |
| BUG-PROD-004 | Low | Data Integrity | Unused `approveAllDraft()` bypasses approval state machine |
