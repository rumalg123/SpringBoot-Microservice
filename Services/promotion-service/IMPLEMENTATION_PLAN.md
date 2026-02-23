# Promotion Service Implementation Plan (Source of Truth)

This file is the implementation source of truth for `promotion-service` and related integrations.

Rules for execution:
- Every completed implementation slice must be checked off here.
- Every completed slice must be committed before moving on.
- This plan may be refined, but completed items should remain documented.

## Confirmed Design Decisions

- Real monetary promotion logic is required (including shipping-related discounts).
- Vendor-created promotions require platform approval by `super_admin` or platform staff with promotion-management permission.
- Promotion pricing remains dynamic in `promotion-service` (not synced into `product-service.discountedPrice`).

## Phase 0: Foundations

- [x] Create promotion-service project foundation aligned with repo conventions (`controller`, `service`, `entity`, `repo`, `dto`, `client`, `config`, `security`, `exception`).
- [x] Add `application.yaml` baseline config (Postgres, Redis, Eureka, resilience4j, internal auth shared secret).
- [x] Add standard error model + global exception handler.
- [x] Add internal request auth verifier (`X-Internal-Auth`).
- [x] Add basic compile/test sanity for `promotion-service`.

## Phase 1: RBAC + Access Scoping

- [x] Add promotion permissions to `access-service` (`platform.promotions.manage`, `vendor.promotions.manage`).
- [x] Add `promotion-service` clients for `access-service` and `vendor-service` internal access lookups.
- [x] Implement `AdminPromotionAccessScopeService` (super admin / platform staff / vendor admin / vendor staff scope resolution).
- [x] Add API gateway routing rule for `/admin/promotions/**` with scoped admin access.

## Phase 2: Core Promotion Domain (MVP)

- [x] Define promotion enums/entities for campaign type, status, funding source, scope/approval status.
- [x] Define coupon entities (`CouponCode`, usage/reservation records) with indexes and constraints.
- [x] Define DTOs + validation for admin create/update/list/approve APIs.
- [x] Implement repositories and base query/filter support.

## Phase 3: Admin Promotion Management APIs (MVP)

- [x] Admin CRUD for promotions (`/admin/promotions`).
- [x] Admin status transitions (activate, pause, archive).
- [x] Vendor submission workflow and platform approval/rejection endpoints.
- [x] Admin list/filter endpoints (status, vendor, type, date range, approval state).

## Phase 4: Promotion Evaluation Engine (MVP)

- [x] Build deterministic pricing engine for line/cart/shipping discounts with priority and exclusivity rules.
- [x] Implement coupon validation (validity window, min spend, usage limits, per-customer limits).
- [x] Add internal quote API (`/internal/promotions/quote`) returning breakdown + rejection reasons.
- [x] Add reservation/commit/release APIs for coupon usage lifecycle.

## Phase 5: Real Monetary Integration (Cart + Order)

- [x] Extend `cart-service` DTOs/entities/responses for coupon input and monetary totals (`subtotal`, `discount`, `shipping`, `grandTotal`) as needed.
- [x] Integrate `cart-service` with promotion quote API for checkout preview.
- [x] Extend `order-service` DTOs/entities to persist promotion snapshot and monetary breakdown.
- [x] Integrate `order-service` with promotion reservation commit/release flow.
- [x] Release/reconcile coupon reservations on cancel/refund paths.

## Phase 6: Shipping Promotion Support

- [x] Add shipping fee modeling (if missing) needed for real free-delivery discounts.
- [x] Apply shipping discounts/free delivery in promotion engine and persist results in order snapshots.

## Phase 7: Tests + Hardening

- [x] Unit tests for access scoping.
- [x] Unit tests for promotion engine conflict/stacking rules.
- [x] Integration tests for coupon reservation lifecycle.
- [x] Integration tests for cart/order promotion flows.
- [x] Seed/sample data for local testing (optional, controlled by config).

## Phase 8: Nice-to-Have (After MVP)

- [x] BOGO / Buy-X-Get-Y support.
- [x] Tiered spend discounts.
- [ ] Bundle discounts.
- [ ] Promotion budgets and burn limits.
- [ ] Analytics/reporting endpoints.
