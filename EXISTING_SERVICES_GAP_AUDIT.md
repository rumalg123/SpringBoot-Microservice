# Existing Services Gap Audit — AliExpress-Scale Readiness

> **What each existing service is missing internally** for a production-ready AliExpress-scale marketplace.
> Generated: 2026-02-24
> Scope: All 12 microservices
> Note: `create-drop` is intentional (dev mode). Services are only accessible via API gateway.

---

## Table of Contents

1. [product-service](#1-product-service)
2. [order-service](#2-order-service)
3. [cart-service](#3-cart-service)
4. [customer-service](#4-customer-service)
5. [vendor-service](#5-vendor-service)
6. [promotion-service](#6-promotion-service)
7. [wishlist-service](#7-wishlist-service)
8. [poster-service](#8-poster-service)
9. [admin-service](#9-admin-service)
10. [access-service](#10-access-service)
11. [api-gateway](#11-api-gateway)
12. [discovery-server](#12-discovery-server)

---

## 1. product-service

### 1.1 Missing Product Features

| Gap | Details | Severity |
|-----|---------|----------|
| ~~**Product specifications/attributes**~~ | ✅ **RESOLVED (Batch 12)** — `ProductSpecification` entity + `CategoryAttribute` entity + `AttributeType` enum. Spec-based filtering via `?specs=RAM:8GB,Material:Cotton`. | ~~HIGH~~ |
| ~~**Brand management**~~ | ✅ **RESOLVED (Batch 5)** — `brandName` added to Product entity + ProductCatalogRead (with `brandNameLc` index). Brand filter param on all list endpoints. Text search (`?q=`) includes brand. | ~~HIGH~~ |
| ~~**Product approval workflow**~~ | ✅ **RESOLVED (Batch 5)** — `ApprovalStatus` enum (DRAFT/PENDING_REVIEW/APPROVED/REJECTED) + `rejectionReason` on Product + read model. 3 endpoints: submit-for-review, approve, reject. Public listing restricted to APPROVED only. Admin filter by `?approvalStatus=`. | ~~HIGH~~ |
| ~~**SEO fields**~~ | ✅ **RESOLVED (Batch 5)** — `metaTitle` (70 chars) + `metaDescription` (160 chars) on Product entity + DTOs. | ~~MEDIUM~~ |
| ~~**Weight/dimensions for shipping**~~ | ✅ **RESOLVED (Batch 5)** — `weightGrams`, `lengthCm`, `widthCm`, `heightCm` on Product entity + DTOs. | ~~HIGH~~ |
| ~~**Digital product support**~~ | ✅ **RESOLVED (Batch 14)** — `DIGITAL` added to `ProductType` enum. `digital` boolean on Product entity. Digital products skip shipping fields. DIGITAL products cannot have variations. | ~~LOW~~ |
| ~~**Product bundling**~~ | ✅ **RESOLVED (Batch 14)** — `bundledProductIds` (`List<UUID>`) on Product entity, stored in `product_bundle_items` collection table. Included in DTOs. | ~~LOW~~ |
| ~~**Bulk operations**~~ | ✅ **RESOLVED (Batch 14)** — `POST /admin/products/bulk-delete`, `/bulk-price-update`, `/bulk-category-reassign`. Max 50 items per request. `BulkOperationResult` with success/failure counts. | ~~HIGH~~ |
| ~~**CSV import/export**~~ | ✅ **RESOLVED (Batch 14)** — `GET /admin/products/export?format=csv` + `POST /admin/products/import` (multipart CSV). Import returns per-row errors. | ~~HIGH~~ |

### 1.2 Missing Category Features

| Gap | Details | Severity |
|-----|---------|----------|
| ~~**Deep category tree**~~ | ✅ **RESOLVED (Batch 13)** — `depth` + `path` fields on Category entity. Configurable max depth (default 4) via `category.max-depth`. Materialized path pattern (`/parent-slug/child-slug/`). | ~~HIGH~~ |
| ~~**Category attributes/templates**~~ | ✅ **RESOLVED (Batch 12)** — `CategoryAttribute` entity defines which attributes are relevant per category with `AttributeType` enum. | ~~HIGH~~ |
| ~~**Category images**~~ | ✅ **RESOLVED (Batch 5)** — `imageUrl` (500 chars) added to Category entity + UpsertCategoryRequest + CategoryResponse. | ~~MEDIUM~~ |
| ~~**Category SEO**~~ | ✅ **RESOLVED (Batch 5)** — `description` (1000 chars) added to Category entity + DTOs. Category landing pages now have descriptive content. | ~~MEDIUM~~ |
| ~~**Category sort order**~~ | ✅ **RESOLVED (Batch 5)** — `displayOrder` (Integer, default 0) added to Category entity + DTOs. Field is stored; query sorting deferred. | ~~MEDIUM~~ |
| ~~**Category pagination**~~ | ✅ **RESOLVED (Batch 13)** — `GET /categories/paged` + `GET /admin/categories/paged` + `GET /admin/categories/deleted/paged`. Supports `type`, `parentCategoryId`, and Pageable. | ~~MEDIUM~~ |

### 1.3 Image Handling Gaps

| Gap | Details | Severity |
|-----|---------|----------|
| ~~**CDN integration**~~ | ⏭️ **SKIPPED** — Cloudflare CDN already configured. | ~~HIGH~~ |
| ~~**Image resize/optimization**~~ | ✅ **RESOLVED (Batch 14)** — Automatic thumbnail generation (300px max) on upload with `-thumb` suffix. WebP supported in ALLOWED_EXTENSIONS. Server-side resizing via `Graphics2D`. | ~~HIGH~~ |
| ~~**Max images cap**~~ | ✅ **RESOLVED (Batch 14)** — Increased from 5 to 10 images per product. `MAX_IMAGES_PER_REQUEST` updated in storage service + DTO validation. | ~~LOW~~ |

### 1.4 Search & Filter Gaps

| Gap | Details | Severity |
|-----|---------|----------|
| ~~**Attribute/specification filter**~~ | ✅ **RESOLVED (Batch 12)** — `?specs=RAM:8GB,Material:Cotton` parameter. Resolves matching product IDs via `ProductSpecificationRepository` then filters catalog read model. | ~~HIGH~~ |
| ~~**Brand filter**~~ | ✅ **RESOLVED (Batch 5)** — `brandName`/`brandNameLc` on ProductCatalogRead with index. `?brand=` filter param + included in text search (`?q=`). | ~~HIGH~~ |
| **Rating/review filter** | 🚫 **BLOCKED** — Needs review-service (not yet built). No rating or review count on any entity or DTO. | HIGH |
| ~~**Sort by popularity/relevance**~~ | ✅ **RESOLVED (Batch 13)** — `viewCount` + `soldCount` fields on Product + ProductCatalogRead. `POST /products/{id}/view` endpoint. `?sortBy=` param with presets: `popularity`, `best-selling`, `most-viewed`, `newest`, `price-low`, `price-high`. | ~~HIGH~~ |
| ~~**Date range filter**~~ | ✅ **RESOLVED (Batch 13)** — `?createdAfter=` + `?createdBefore=` (Instant) on product listing endpoint. | ~~LOW~~ |
| ~~**Vendor name search**~~ | ✅ **RESOLVED (Batch 13)** — `vendorName` + `vendorNameLc` denormalized on ProductCatalogRead. `?vendorName=` ILIKE filter. Vendor name resolved from vendor-service via client. | ~~MEDIUM~~ |

### 1.5 Validation Gaps

| Gap | Details | Severity |
|-----|---------|----------|
| ~~**PARENT→SINGLE type change**~~ | ✅ **RESOLVED (Batch 13)** — Guard prevents type change from PARENT to SUB when active child categories exist. Throws `ValidationException`. | ~~MEDIUM~~ |
| ~~**Zero discounted price**~~ | ✅ **RESOLVED (Batch 13)** — `validatePricing()` rejects `discountedPrice <= 0`. DTO uses `@DecimalMin(value = "0.00", inclusive = false)`. | ~~LOW~~ |
| ~~**Read model rebuild**~~ | ✅ **RESOLVED (Batch 14)** — `rebuildAll()` now processes in configurable page-size batches (default 100 via `catalog.rebuild.page-size`). Progress logging per page. | ~~MEDIUM~~ |
| ~~**No image upload rate limit**~~ | ✅ **RESOLVED (Batch 14)** — `ImageUploadRateLimiter` (Redis-based, 60 images/hour per user). Configurable via `product.image.upload.rate-limit-per-hour`. | ~~LOW~~ |

---

## 2. order-service

### 2.1 Order Lifecycle Gaps

| Gap | Details | Severity |
|-----|---------|----------|
| ~~**Missing statuses**~~ | ✅ **RESOLVED (Batch 8)** — `PAYMENT_PENDING`, `PAYMENT_FAILED`, `PARTIALLY_SHIPPED`, `PARTIALLY_DELIVERED`, `PARTIALLY_REFUNDED`, `DISPUTE_OPEN`, `DISPUTE_RESOLVED`, `ON_HOLD` statuses added. | ~~HIGH~~ |
| ~~**No customer self-cancellation**~~ | ✅ **RESOLVED (Batch 1)** — `POST /orders/me/{id}/cancel` endpoint added with ownership verification and cancellable-status guard. | ~~HIGH~~ |
| ~~**No order expiry for unpaid orders**~~ | ✅ **RESOLVED (Batch 8)** — `expiresAt` field, TTL logic, and scheduled cleanup added. Unpaid orders are now expired automatically. | ~~HIGH~~ |
| ~~**Aggregate status bug**~~ | ✅ **RESOLVED (Batch 15)** — `deriveAggregateOrderStatus` now handles CANCELLED + active mix correctly. | ~~MEDIUM~~ |
| ~~**Incomplete state transitions**~~ | ✅ **RESOLVED (Batch 15)** — `RETURN_REJECTED` status added. Return flow now supports rejection path. | ~~MEDIUM~~ |

### 2.2 Missing Order Features

| Gap | Details | Severity |
|-----|---------|----------|
| ~~**Order notes**~~ | ✅ **RESOLVED (Batch 4)** — `customerNote` (500 chars) + `adminNote` (1000 chars) on Order. Customer note in create request. Admin note via `PATCH /orders/{id}/note`. Admin note hidden from customer view. | ~~MEDIUM~~ |
| ~~**Tracking number / shipment info**~~ | ✅ **RESOLVED (Batch 1)** — `trackingNumber`, `trackingUrl`, `carrierCode`, `estimatedDeliveryDate` added to Order + VendorOrder + DTOs. `PATCH /orders/{id}/tracking` endpoint. | ~~HIGH~~ |
| ~~**Payment reference**~~ | ✅ **RESOLVED (Batch 1)** — `paymentId`, `paymentMethod`, `paymentGatewayRef`, `paidAt` added to Order entity + DTOs. `PATCH /orders/{id}/payment` endpoint. | ~~HIGH~~ |
| ~~**Invoice generation**~~ | ✅ **RESOLVED (Batch 16)** — `GET /orders/{id}/invoice` endpoint. `InvoiceResponse` DTO with line items. | ~~MEDIUM~~ |
| ~~**Order export**~~ | ✅ **RESOLVED (Batch 16)** — `GET /orders/export?format=csv` with status/date filters. Admin passthrough via `GET /admin/orders/export`. | ~~MEDIUM~~ |
| ~~**Partial fulfillment**~~ | ✅ **RESOLVED (Batch 15)** — `fulfilledQuantity` + `cancelledQuantity` on OrderItem. Quantities tracked independently. | ~~MEDIUM~~ |
| ~~**Order editing**~~ | ✅ **RESOLVED (Batch 16)** — `PATCH /orders/{id}/shipping-address` endpoint. Only allowed for PENDING/PAYMENT_PENDING/CONFIRMED orders. | ~~LOW~~ |
| ~~**Currency field**~~ | ✅ **RESOLVED (Batch 4)** — `currency` (3 chars, default "USD") on Order + VendorOrder + all response DTOs. | ~~HIGH~~ |
| ~~**`updatedAt` on Order**~~ | ✅ **RESOLVED (Batch 4)** — `@UpdateTimestamp updatedAt` on Order + VendorOrder + all response DTOs. | ~~LOW~~ |

### 2.3 Vendor Sub-Order Gaps

| Gap | Details | Severity |
|-----|---------|----------|
| ~~**No vendor-readable endpoint**~~ | ✅ **RESOLVED (Batch 8)** — `GET /orders/vendor/me` endpoint added. Vendors can now list their own orders without `X-Internal-Auth`. | ~~HIGH~~ |
| ~~**No financial breakdown**~~ | ✅ **RESOLVED (Batch 15)** — `discountAmount`, `shippingAmount`, `platformFee`, `payoutAmount` on VendorOrder. Full financial breakdown per vendor sub-order. | ~~HIGH~~ |
| ~~**No shipping fields on VendorOrder**~~ | ✅ **RESOLVED (Batch 1)** — `trackingNumber`, `trackingUrl`, `carrierCode`, `estimatedDeliveryDate` added to VendorOrder + DTOs. | ~~HIGH~~ |
| ~~**No `updatedAt`**~~ | ✅ **RESOLVED (Batch 4)** — `@UpdateTimestamp updatedAt` added to VendorOrder entity + VendorOrderResponse. | ~~LOW~~ |

### 2.4 Refund/Return Gaps

| Gap | Details | Severity |
|-----|---------|----------|
| **No outbound events on refund** | 🚫 **BLOCKED** — Needs notification-service + payment-service (not yet built). | HIGH |
| ~~**No refund amount field**~~ | ✅ **RESOLVED (Batch 8)** — `refundedAmount`, `refundReason`, `refundInitiatedAt` fields added. | ~~HIGH~~ |
| ~~**No partial refund**~~ | ✅ **RESOLVED (Batch 15)** — Partial refund support with configurable refund amounts. No longer all-or-nothing. | ~~HIGH~~ |
| **No return logistics** | No `returnAddressId`, return label fields, or ReturnItem entity. | MEDIUM |

### 2.5 Customer vs Admin Endpoint Gaps

| Gap | Details | Severity |
|-----|---------|----------|
| ~~**No customer status history**~~ | ✅ **RESOLVED (Batch 4)** — `GET /orders/me/{id}/status-history` endpoint with ownership verification. | ~~HIGH~~ |
| ~~**No customer vendor sub-order view**~~ | ✅ **RESOLVED (Batch 15)** — Customers can see which vendor shipped which items via sub-order view. | ~~MEDIUM~~ |
| ~~**No status filter**~~ | ✅ **RESOLVED (Batch 4)** — `?status=` param on `GET /orders`, `GET /orders/me`, `GET /admin/orders`. Uses `JpaSpecificationExecutor` + `OrderSpecifications` for dynamic filtering. | ~~HIGH~~ |
| ~~**No date range filter**~~ | ✅ **RESOLVED (Batch 4)** — `?createdAfter=` + `?createdBefore=` on all list endpoints (order-service + admin-service passthrough). | ~~MEDIUM~~ |
| ~~**Per-line discount missing**~~ | ✅ **RESOLVED (Batch 15)** — `discountAmount` added to OrderItem. Customers can see per-item savings. | ~~LOW~~ |

### 2.6 Validation Issues

| Gap | Details | Severity |
|-----|---------|----------|
| **No stock validation at order placement** | 🚫 **BLOCKED** — Needs inventory-service (not yet built). | HIGH |
| ~~**`UpdateOrderStatusRequest` has no reason**~~ | ✅ **RESOLVED (Batch 16)** — `reason` field added. Stored in audit trail. | ~~MEDIUM~~ |
| ~~**Null `grandTotal` passes validation**~~ | ✅ **RESOLVED (Batch 16)** — `@NotNull` on all `PromotionCheckoutPricingRequest` fields. `grandTotal` has `@DecimalMin("0.01")`. | ~~MEDIUM~~ |
| ~~**`Order.item` is summary string**~~ | ✅ **RESOLVED (Batch 16)** — Shows "Product Name + N more items" instead of "Multiple items". | ~~LOW~~ |

---

## 3. cart-service

| Gap | Details | Severity |
|-----|---------|----------|
| ~~**No cart expiry / TTL**~~ | ✅ **RESOLVED (Batch 11)** — Cart expiry with TTL. Abandoned carts cleaned up automatically. | ~~HIGH~~ |
| ~~**No guest cart**~~ | ⏭️ **SKIPPED** — Cart is login-only by design. Guest users can only browse. | ~~HIGH~~ |
| ~~**No cart merge on login**~~ | ⏭️ **SKIPPED** — No guest cart = no merge needed. | ~~HIGH~~ |
| ~~**No save for later**~~ | ✅ **RESOLVED (Batch 11)** — Save-for-later functionality added. Items can be moved between cart and saved list. | ~~MEDIUM~~ |
| ~~**No cart-level notes**~~ | ✅ **RESOLVED (Batch 11)** — Cart-level notes/instructions field added. | ~~LOW~~ |
| **No stock quantity check** | 🚫 **BLOCKED** — Needs inventory-service (not yet built). | HIGH |
| ~~**Category IDs missing from snapshot**~~ | ✅ **RESOLVED (Batch 11)** — Category IDs included in product snapshot. Enables category-scoped promotions in cart. | ~~MEDIUM~~ |

**What's GOOD:** Max 200 distinct items, quantity cap 1-1000, vendor operational state check, price refresh on update, price staleness detection at checkout, idempotent checkout.

---

## 4. customer-service

| Gap | Details | Severity |
|-----|---------|----------|
| ~~**Thin profile**~~ | ✅ **RESOLVED (Batch 3)** — `phone`, `avatarUrl`, `dateOfBirth`, `gender` (enum) added to Customer entity + UpdateCustomerProfileRequest + CustomerResponse. | ~~HIGH~~ |
| ~~**No account deactivation/deletion**~~ | ✅ **RESOLVED (Batch 3)** — `active`, `deactivatedAt` fields on Customer. `POST /customers/me/deactivate` endpoint. Keycloak user disabled on deactivation. Active guard on all `/me` mutation + address endpoints. | ~~HIGH~~ |
| ~~**No address validation**~~ | ✅ **RESOLVED (Batch 19)** — Improved address validation with postal code, city, and country consistency checks. | ~~MEDIUM~~ |
| ~~**No customer tier/loyalty**~~ | ✅ **RESOLVED (Batch 19)** — `CustomerLoyaltyTier` enum (BRONZE/SILVER/GOLD/PLATINUM). `loyaltyPoints` and tier tracking on Customer entity. | ~~MEDIUM~~ |
| ~~**No communication preferences**~~ | ✅ **RESOLVED (Batch 19)** — `CommunicationPreferences` entity with marketing email, SMS, push notification opt-in/out and locale preference. | ~~MEDIUM~~ |
| ~~**No activity log**~~ | ✅ **RESOLVED (Batch 19)** — `CustomerActivityLog` entity tracking customer actions with timestamps. | ~~LOW~~ |
| ~~**No social login linking**~~ | ✅ **RESOLVED (Batch 19)** — Endpoint to link additional OAuth providers to existing customer account. | ~~LOW~~ |
| ~~**No `updatedAt`**~~ | ✅ **RESOLVED (Batch 3)** — `updatedAt` added to Customer entity + CustomerResponse. | ~~LOW~~ |

**What's GOOD:** Full address CRUD with soft-delete, default shipping/billing rebalancing, max 50 addresses, identity registration from Keycloak SSO.

---

## 5. vendor-service

| Gap | Details | Severity |
|-----|---------|----------|
| **No verification/certification** | No VERIFIED status tier, no `verifiedAt`, no verification workflow. Buyers have no trust badge signal. | HIGH |
| **No performance metrics** | No `averageRating`, `fulfillmentRate`, `disputeRate`, `responseTimeHours`. Buyers have no vendor quality signal. | HIGH |
| ~~**No commission/fee structure**~~ | ✅ **RESOLVED (Batch 2)** — `commissionRate` (BigDecimal) added to Vendor entity + admin DTO. | ~~HIGH~~ |
| ~~**No payout configuration**~~ | ✅ **RESOLVED (Batch 2)** — `VendorPayoutConfig` entity + repository + DTOs (`UpsertVendorPayoutConfigRequest`, `VendorPayoutConfigResponse`). `PayoutSchedule` enum. Managed via vendor self-service endpoints. | ~~HIGH~~ |
| **No return/shipping policy** | No `returnPolicy`, `shippingPolicy`, `processingTimeDays` fields. Buyers can't read vendor terms. | HIGH |
| ~~**No self-service vendor portal**~~ | ✅ **RESOLVED (Batch 2)** — 6 endpoints at `/vendors/me` (GET profile, PUT profile, GET payout config, PUT payout config, POST stop-orders, POST resume-orders). Gateway routing + rate limiting + idempotency configured. | ~~HIGH~~ |
| ~~**No vacation mode**~~ | ✅ **RESOLVED (Batch 2)** — Stop/resume orders via `/vendors/me/stop-orders` and `/vendors/me/resume-orders` self-service endpoints. | ~~MEDIUM~~ |
| **No vendor categories** | No `categories`, `specializations`, or `primaryCategory`. Can't browse "Electronics vendors". | MEDIUM |
| **Description capped at 500 chars** | Too short for a real storefront. Should be several thousand or TEXT/CLOB. | LOW |

**What's GOOD:** Logo, banner, description, contact fields, two-step delete with eligibility check, stop/resume orders, full lifecycle audit, multi-role vendor user memberships.

---

## 6. promotion-service

| Gap | Details | Severity |
|-----|---------|----------|
| ~~**No public promotion browsing**~~ | ✅ **RESOLVED (Batch 6)** — `GET /promotions` + `GET /promotions/{id}` public endpoints. Filters ACTIVE + APPROVED + time-valid only. `PublicPromotionResponse` DTO excludes sensitive fields. Gateway route + security + rate limiter. | ~~HIGH~~ |
| ~~**No batch coupon generation**~~ | ✅ **RESOLVED (Batch 6)** — `POST /admin/promotions/{id}/coupons/batch` generates up to 10,000 unique codes with prefix + 8-char random alphanumeric. `BatchCreateCouponsRequest`/`Response` DTOs. | ~~HIGH~~ |
| ~~**No coupon deactivation**~~ | ✅ **RESOLVED (Batch 6)** — `PATCH /admin/promotions/{id}/coupons/{couponId}/deactivate` and `/activate` endpoints. Reuses existing `active` boolean on CouponCode entity. | ~~HIGH~~ |
| ~~**No customer segment targeting**~~ | ✅ **RESOLVED (Batch 17)** — `targetSegments` field on promotions. Quote engine checks customer segment identity. | ~~HIGH~~ |
| ~~**No flash sale support**~~ | ✅ **RESOLVED (Batch 17)** — `isFlashSale`, `flashSaleStartAt`/`EndAt`, `flashSaleMaxRedemptions` fields. `GET /promotions/flash-sales` endpoint. | ~~HIGH~~ |
| **No referral/affiliate support** | 🚫 **BLOCKED** — Needs referral/affiliate service (not yet built). | MEDIUM |
| ~~**Incomplete stacking rules**~~ | ✅ **RESOLVED (Batch 17)** — `stackingGroup` + `maxStackCount` fields. Type-level stacking rules. | ~~MEDIUM~~ |
| ~~**No timezone on scheduling**~~ | ✅ **RESOLVED (Batch 17)** — Timezone support on `startsAt`/`endsAt`. Can define "midnight Beijing time" natively. | ~~MEDIUM~~ |
| ~~**No customer usage history query**~~ | ✅ **RESOLVED (Batch 6)** — `GET /promotions/me/coupon-usage` returns paginated committed reservations with coupon code, promotion name, discount amount, order ID, and committed timestamp. | ~~LOW~~ |

**What's GOOD:** 6 benefit types (percentage, fixed, free shipping, buy-x-get-y, tiered spend, bundle), two-phase coupon reservation (reserve→commit/release), budget/burn tracking, approval workflow (submit→approve/reject), lifecycle (draft→active→paused→archived), per-promotion analytics, access scoping.

---

## 7. wishlist-service

| Gap | Details | Severity |
|-----|---------|----------|
| ~~**Single flat wishlist only**~~ | ✅ **RESOLVED (Batch 18)** — `WishlistCollection` entity. Named collections CRUD (up to 20 per customer). | ~~HIGH~~ |
| **No price drop alerts** | 🚫 **BLOCKED** — Needs notification-service (not yet built). | HIGH |
| **No back-in-stock alerts** | 🚫 **BLOCKED** — Needs inventory-service + notification-service (not yet built). | HIGH |
| ~~**No wishlist sharing**~~ | ✅ **RESOLVED (Batch 18)** — `shareToken` field. Public browse via share link. | ~~MEDIUM~~ |
| ~~**No move-to-cart integration**~~ | ✅ **RESOLVED (Batch 18)** — Server-side `moveToCart()` via `CartClient`. Atomic add-to-cart + remove-from-wishlist. | ~~MEDIUM~~ |
| ~~**No wishlist size limit**~~ | ✅ **RESOLVED (Batch 18)** — 200 items per collection, 20 collections per customer. | ~~MEDIUM~~ |
| ~~**No item notes**~~ | ✅ **RESOLVED (Batch 18)** — `note` field on WishlistItem. | ~~LOW~~ |

**What's GOOD:** Add, remove (by item ID or product ID), clear all, product snapshot on add, upsert (no duplicates).

---

## 8. poster-service

| Gap | Details | Severity |
|-----|---------|----------|
| ~~**No click tracking**~~ | ✅ **RESOLVED (Batch 7)** — `clickCount` + `lastClickAt` on Poster entity. Atomic DB increment via `POST /posters/{id}/click` (204 No Content). No cache invalidation on tracking. | ~~HIGH~~ |
| ~~**No impression tracking**~~ | ✅ **RESOLVED (Batch 7)** — `impressionCount` + `lastImpressionAt` on Poster entity. Atomic DB increment via `POST /posters/{id}/impression` (204 No Content). | ~~HIGH~~ |
| ~~**No A/B testing**~~ | ✅ **RESOLVED (Batch 22)** — `PosterVariant` entity with weighted A/B testing. Click/impression tracking per variant. Admin CRUD for variants. | ~~HIGH~~ |
| ~~**No audience targeting**~~ | ✅ **RESOLVED (Batch 7)** — `targetCountries` (ElementCollection, Set of country codes) + `targetCustomerSegment` (String, 40 chars) on Poster entity + DTOs. Empty targetCountries = show everywhere. | ~~MEDIUM~~ |
| ~~**No performance metrics endpoint**~~ | ✅ **RESOLVED (Batch 7)** — `PosterAnalyticsResponse` DTO with clickCount, impressionCount, CTR. `GET /admin/posters/analytics` (all) + `GET /admin/posters/{id}/analytics` (single). | ~~MEDIUM~~ |
| ~~**Minimal responsive images**~~ | ✅ **RESOLVED (Batch 22)** — `tabletImage`, `srcsetDesktop`, `srcsetMobile`, `srcsetTablet` fields. Full responsive image support. | ~~LOW~~ |

**What's GOOD:** 8 placement types, 6 size presets, scheduling (startAt/endAt), link types (product/category/search/URL), soft-delete + restore, image upload, slug management.

---

## 9. admin-service

| Gap | Details | Severity |
|-----|---------|----------|
| ~~**No dashboard/analytics endpoints**~~ | ✅ **RESOLVED (Batch 20)** — `DashboardService` with aggregate stats. Dashboard endpoints for revenue, order counts, user metrics. | ~~HIGH~~ |
| ~~**No bulk operations**~~ | ✅ **RESOLVED (Batch 20)** — `POST /admin/orders/bulk-status-update` (max 50 orders). `BulkOperationResult` with success/failure counts and per-item errors. | ~~HIGH~~ |
| ~~**No admin action audit log**~~ | ✅ **RESOLVED (Batch 20)** — `AdminAuditService.log()` on all admin controllers. Audit logging on: order status updates, vendor CRUD, vendor lifecycle, poster CRUD, bulk operations. | ~~HIGH~~ |
| ~~**No export functionality**~~ | ✅ **RESOLVED (Batch 16/20)** — `GET /admin/orders/export?format=csv` with status/date filters. CSV export passthrough to order-service. | ~~MEDIUM~~ |
| **No system configuration** | No feature flags, maintenance mode, or runtime platform settings management. | MEDIUM |
| **No impersonation** | Super admins can't view platform as a customer for support purposes. | LOW |
| ~~**Unbounded list responses**~~ | ✅ **RESOLVED (Batch 20)** — Paginated list responses with `PageResponse<T>` wrapper. | ~~MEDIUM~~ |

**What's GOOD:** RBAC scoping (super_admin sees all, vendor_admin sees own vendor), circuit breaker + retry on outbound calls, capabilities introspection, Keycloak user search, idempotency on mutations, two-step vendor delete, session revocation on staff deactivation.

---

## 10. access-service

| Gap | Details | Severity |
|-----|---------|----------|
| ~~**Missing platform permissions**~~ | ✅ **RESOLVED (Batch 21)** — Expanded to 13 platform permissions including `VENDORS_READ`, `VENDORS_MANAGE`, `CUSTOMERS_MANAGE`, `FINANCE_READ`, `AUDIT_READ`. | ~~HIGH~~ |
| ~~**Missing vendor permissions**~~ | ✅ **RESOLVED (Batch 21)** — Expanded to 8 vendor permissions including `REPORTS_READ`, `CUSTOMERS_READ`, `SETTINGS_MANAGE`. | ~~MEDIUM~~ |
| ~~**No permission groups/presets**~~ | ✅ **RESOLVED (Batch 21)** — `PermissionGroup` entity. Role templates like "Order Manager", "Product Editor" with pre-configured permission sets. | ~~MEDIUM~~ |
| ~~**No time-limited access**~~ | ✅ **RESOLVED (Batch 21)** — `accessExpiresAt` field on access records. `AccessExpiryScheduler` for automatic deactivation. | ~~MEDIUM~~ |
| ~~**No MFA enforcement**~~ | ✅ **RESOLVED (Batch 21)** — `mfaRequired` flag. MFA enforcement for admin/staff access. | ~~MEDIUM~~ |
| ~~**No IP-based restrictions**~~ | ✅ **RESOLVED (Batch 21)** — `allowedIps` field on access records. Staff restricted to allowed IP ranges. | ~~LOW~~ |
| ~~**No session management**~~ | ✅ **RESOLVED (Batch 21)** — `ActiveSession` entity. Session listing, `lastLoginAt` tracking. | ~~LOW~~ |
| ~~**No API key management**~~ | ✅ **RESOLVED (Batch 21)** — `ApiKey` entity with hash storage. API key CRUD for third-party integrations (ERP, fulfillment). | ~~LOW~~ |

**What's GOOD:** 13 platform + 8 vendor permissions, full audit trail (actor, role, reason, permissions snapshot), cache-backed lookups (20s TTL), soft-delete + restore, Keycloak session revocation on deactivation, permission groups, time-limited access, MFA, sessions, API keys.

---

## 11. api-gateway

| Gap | Details | Severity |
|-----|---------|----------|
| ~~**No circuit breaker on routes**~~ | ✅ **RESOLVED (Batch 10)** — Resilience4J circuit breakers on all routes. Downstream hangs no longer block gateway. | ~~HIGH~~ |
| ~~**No request/response logging**~~ | ✅ **RESOLVED (Batch 10)** — Structured access logging (method, path, status, latency). Request correlation via `X-Request-Id`. | ~~HIGH~~ |
| ~~**No request body size limit**~~ | ✅ **RESOLVED (Batch 10)** — `max-request-size` configured. Body reads capped in idempotency filter. | ~~MEDIUM~~ |
| ~~**No API versioning**~~ | ✅ **RESOLVED (Batch 10)** — API versioning support on routes. | ~~MEDIUM~~ |
| ~~**No IP blocking/allowlisting**~~ | ✅ **RESOLVED (Batch 10)** — IP blocking/allowlisting mechanism for admin routes. | ~~MEDIUM~~ |
| ~~**Rate limit headers not exposed to browsers**~~ | ✅ **RESOLVED (Batch 10)** — `X-RateLimit-Remaining`, `Retry-After`, `X-Request-Id` added to CORS exposed headers. | ~~LOW~~ |

**What's GOOD:** Redis token-bucket rate limiting (25+ policies, per-user-or-IP keying, Cloudflare-aware IP resolution, fail-open/closed configurable), idempotency enforcement, JWT validation with audience + issuer, namespace-aware role extraction, email_verified enforcement, header sanitization before relay, health endpoints, circuit breakers, structured logging.

---

## 12. discovery-server

Netflix Eureka server. Pure service registry — no business logic, no gaps. Does its job.

**Consideration for scale:** At AliExpress scale, Eureka may need to be replaced with Kubernetes-native service discovery or Consul for better partition tolerance and faster health propagation.

---

## Priority Summary

> All implementable gaps from Batches 1–22 have been resolved. Only blocked items remain.

### 🚫 Blocked (depends on services not yet built)

| Gap | Service | Needs | Severity |
|-----|---------|-------|----------|
| Rating/review filter | product-service | **review-service** | HIGH |
| No stock validation at order placement | order-service | **inventory-service** | HIGH |
| No outbound events on refund | order-service | **notification-service + payment-service** | HIGH |
| No stock quantity check (cart) | cart-service | **inventory-service** | HIGH |
| No price drop alerts | wishlist-service | **notification-service** | HIGH |
| No back-in-stock alerts | wishlist-service | **inventory-service + notification-service** | HIGH |
| No referral/affiliate support | promotion-service | **referral/affiliate service** | MEDIUM |

### Remaining (not blocked, lower priority)

| Gap | Service | Severity |
|-----|---------|----------|
| No vendor verification/certification | vendor-service | HIGH |
| No vendor performance metrics | vendor-service | HIGH |
| No vendor return/shipping policy | vendor-service | HIGH |
| No vendor categories | vendor-service | MEDIUM |
| Vendor description capped at 500 chars | vendor-service | LOW |
| No return logistics (order-service) | order-service | MEDIUM |
| No system configuration / feature flags | admin-service | MEDIUM |
| No impersonation | admin-service | LOW |

### Resolution Statistics

| Category | Count |
|----------|-------|
| **Resolved (Batches 1–22)** | ~80 gaps |
| **Blocked (needs new services)** | 7 gaps |
| **Skipped (not needed / already handled)** | 3 gaps |
| **Remaining (implementable, lower priority)** | 8 gaps |
