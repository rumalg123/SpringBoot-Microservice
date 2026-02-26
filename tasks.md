# Full-Stack Review: Tasks

> Generated from comprehensive review of both the Spring Boot backend (`Services/`) and Next.js frontend (`microservce-frontend/`).
> Each task lists the **issue**, the **files** to change, and the **exact changes** required.

---

## Backend Tasks

---

### B1 — Add `vendorName` to `VendorOrderResponse`

**Issue:** The frontend `VendorOrder` type expects an optional `vendorName` field, but the backend `VendorOrderResponse` only contains `vendorId`. Vendor name must be resolved so the UI can display it without a separate lookup.

- **`Services/order-service/src/main/java/com/rumal/order_service/dto/VendorOrderResponse.java`**
  - Add field `String vendorName` after `vendorId`.

- **`Services/order-service/src/main/java/com/rumal/order_service/service/OrderService.java`** (the `toVendorOrderResponse` mapper method)
  - Populate `vendorName` from the `VendorOrder` entity. If the entity doesn't store vendor name, either:
    - (a) Embed `vendorName` into the `VendorOrder` entity at order-creation time (denormalized, preferred for read performance), or
    - (b) Fetch it from vendor-service via the existing `VendorClient` and populate during response mapping.

---

### B2 — Add class-level `@Transactional` defaults to services missing them

**Issue:** Several services use method-level `@Transactional` only. Adding a class-level default (`readOnly = true, isolation = READ_COMMITTED, timeout = 10`) provides a safe baseline and prevents accidental non-transactional reads.

- **`Services/inventory-service/src/main/java/com/rumal/inventory_service/service/StockService.java`** (line 27)
  - Add `@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)` at class level.
  - Verify all existing write methods already have explicit `@Transactional(readOnly = false, ...)` overrides.

- **`Services/payment-service/src/main/java/com/rumal/payment_service/service/PaymentService.java`** (line 36)
  - Add `@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)` at class level.
  - Update `initiatePayment` (line 51): change bare `@Transactional` to `@Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)`.
  - Audit all other methods — ensure each write method has explicit `readOnly = false` with appropriate isolation.

- **`Services/payment-service/src/main/java/com/rumal/payment_service/service/RefundService.java`** (line 35)
  - Add `@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)` at class level.
  - Update `createRefundRequest` (line 48): change bare `@Transactional` to `@Transactional(readOnly = false, isolation = Isolation.REPEATABLE_READ, timeout = 20)`.
  - Audit all other mutating methods similarly.

---

### B3 — Add `@Transactional(readOnly = true)` to OrderService read-only methods

**Issue:** Several read-only methods in `OrderService` lack `@Transactional` annotations, meaning they run outside a transaction context (no consistent snapshot isolation, no timeout enforcement).

- **`Services/order-service/src/main/java/com/rumal/order_service/service/OrderService.java`**
  - **`getStatusHistory`** (line 295): Add `@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)`.
  - **`getVendorOrders`** (line 358): Add `@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)`.
  - **`getVendorOrder`** (line 366): Add `@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)`.
  - **`getVendorOrderStatusHistory`** (line 372): Add `@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 10)`.

---

### B4 — Replace raw `RuntimeException` in OrderController CSV export

**Issue:** `OrderController.java` line 281 throws a bare `RuntimeException("Failed to write CSV export", e)`. This bypasses the `GlobalExceptionHandler` pattern and returns an unstructured 500.

- **`Services/order-service/src/main/java/com/rumal/order_service/controller/OrderController.java`** (line 281)
  - Replace `throw new RuntimeException(...)` with a custom exception. Either:
    - Create `CsvExportException extends RuntimeException` in the exceptions package, or
    - Use the existing `ValidationException` if appropriate: `throw new ValidationException("Failed to write CSV export: " + e.getMessage())`.
  - Add a corresponding `@ExceptionHandler(CsvExportException.class)` in `GlobalExceptionHandler` that returns a 500 with the standard error body.

---

### B5 — Targeted cache eviction for category service

**Issue:** `CategoryServiceImpl` uses `@CacheEvict(cacheNames = "categoriesList", allEntries = true)` and `@CacheEvict(cacheNames = "deletedCategoriesList", allEntries = true)` on every create/update/delete. While categories change infrequently, the system already has a `productCacheVersionService.bumpAllProductReadCaches()` pattern that could be extended for categories to avoid broad eviction.

- **`Services/product-service/src/main/java/com/rumal/product_service/service/CategoryServiceImpl.java`** (lines ~54-56, ~80-82, ~107-109, ~132-134)
  - Evaluate whether targeted eviction is practical here. Categories are tree-structured (parent+sub) and list endpoints depend on type and parentCategoryId filters. If the cache key strategy makes targeted eviction complex, document in a code comment why `allEntries = true` is acceptable (rarely changing data per backend constraints).
  - If refactoring: use a category cache version bump pattern similar to `productCacheVersionService` to invalidate only affected cache entries.

---

### B6 — Add `@EntityGraph` to VendorUserRepository queries

**Issue:** `VendorUserRepository.findByVendorIdOrderByRoleAscCreatedAtAsc()` and similar methods that return `VendorUser` entities with `@ManyToOne` relationship to `Vendor` may trigger N+1 queries if the relationship is lazy-loaded.

- **`Services/vendor-service/src/main/java/com/rumal/vendor_service/repo/VendorUserRepository.java`** (lines 15-16)
  - Add `@EntityGraph(attributePaths = {"vendor"})` to query methods that return `VendorUser` if the vendor relationship is accessed in the mapper.
  - Verify by checking the `toVendorUserResponse` mapper — if it accesses `vendorUser.getVendor()`, the `@EntityGraph` is needed.

---

## Frontend Tasks

---

### F1 — Remove extra fields from `CartItem` type (API parity)

**Issue:** The frontend `CartItem` type has `vendorId`, `regularPrice`, and `discountedPrice` fields that the backend `CartItemResponse` does not return. These fields will always be `undefined` at runtime.

- **`microservce-frontend/lib/types/cart.ts`** (lines 7, 10-11)
  - Remove `vendorId: string;` (line 7).
  - Remove `regularPrice: number;` (line 10).
  - Remove `discountedPrice: number | null;` (line 11).

- **All files referencing `cartItem.vendorId`, `cartItem.regularPrice`, `cartItem.discountedPrice`**
  - Search for usages and remove/replace. If `regularPrice` is displayed in the cart UI, use `unitPrice` instead. If `vendorId` is used for grouping, consider whether it should be added to the backend (separate from this task).

---

### F2 — Add `stackingGroup` to `PublicPromotion` type (API parity)

**Issue:** Backend `PublicPromotionResponse` includes a `stackingGroup` (String) field that the frontend `PublicPromotion` type doesn't model. This data is lost on the frontend.

- **`microservce-frontend/lib/types/promotion.ts`** (after line 19, the `stackable: boolean` field)
  - Add `stackingGroup: string | null;` after the `stackable` field.

---

### F3 — Replace UUID text inputs with searchable dropdowns in StockItemForm

**Issue:** `StockItemForm.tsx` forces users to type raw UUIDs for Product ID and Vendor ID. The codebase already has a `SearchableSelect` component that is not used here.

- **`microservce-frontend/app/components/inventory/StockItemForm.tsx`** (lines 26-33)
  - Replace the Product ID `<input>` (line 27) with a `SearchableSelect` that fetches products from `/admin/products` (or `/products` with a search query).
  - Replace the Vendor ID `<input>` (line 32) with a `SearchableSelect` that fetches vendors from `/admin/vendors`.
  - Both selects should search by name/SKU and display the name, storing the UUID as the value.

- **`microservce-frontend/app/components/inventory/StockTab.tsx`**
  - Pass the necessary product/vendor data (or fetch functions) to `StockItemForm`.
  - The parent component should provide a fetch function or pre-loaded list for the searchable selects.

- **`microservce-frontend/app/components/ui/SearchableSelect.tsx`**
  - Verify this component accepts an async fetch function for options. If it only accepts static options, extend it to support async search.

---

### F4 — Add `error.tsx` boundary files for route segments

**Issue:** No `error.tsx` files exist anywhere in the app directory. When an unhandled error occurs, users see a white screen instead of a friendly error page.

- **Create the following files** (each as a `"use client"` component that receives `{ error, reset }` props):
  - `microservce-frontend/app/error.tsx` — Root error boundary.
  - `microservce-frontend/app/admin/error.tsx` — Admin section error boundary.
  - `microservce-frontend/app/vendor/error.tsx` — Vendor section error boundary.
  - `microservce-frontend/app/cart/error.tsx` — Cart error boundary.
  - `microservce-frontend/app/products/[id]/error.tsx` — Product detail error boundary.
  - `microservce-frontend/app/orders/error.tsx` — Orders error boundary.

- Each file should:
  - Display the error message with a "Try Again" button (calling `reset()`).
  - Use theme tokens (`text-danger`, `bg-surface`, etc.) for consistency.
  - Log the error to console in development.

---

### F5 — Add `loading.tsx` skeleton files for route segments

**Issue:** No `loading.tsx` files exist. Pages show nothing during the initial data fetch, leading to perceived slowness.

- **Create the following files** (each as a React component rendering a skeleton/spinner):
  - `microservce-frontend/app/loading.tsx` — Root loading state.
  - `microservce-frontend/app/admin/loading.tsx` — Admin loading skeleton (use `AdminPageShell` with a spinner).
  - `microservce-frontend/app/vendor/loading.tsx` — Vendor loading skeleton.
  - `microservce-frontend/app/products/[id]/loading.tsx` — Product detail skeleton.

- Each should use the existing theme tokens and provide a minimal, non-jarring loading indicator.

---

### F6 — Extract hardcoded colors to CSS variables / theme constants

**Issue:** ~30+ files use hardcoded hex/rgba colors instead of CSS custom properties defined in `globals.css`. This breaks theme consistency and makes future theme changes difficult.

**Sub-task F6a — Dashboard chart colors:**
- **`microservce-frontend/app/components/admin/dashboard/helpers.tsx`** (line 5)
  - The `COLORS` array (`["#00d4ff", "#7c3aed", "#34d399", ...]`) and `CHART_TEXT` (`"#6868a0"`) are shared constants. Keep them here but reference CSS variable values where possible, or document them as the canonical chart palette.

- **`microservce-frontend/app/components/admin/dashboard/OverviewTab.tsx`**, **`CustomersTab.tsx`**, **`InventoryTab.tsx`**, **`VendorsTab.tsx`**, **`PromotionsTab.tsx`**, **`ReviewsTab.tsx`**
  - Replace inline hex colors like `text-[#34d399]` with semantic classes: `text-success` (for green/positive), `text-warning` (for yellow/caution `#fbbf24`), `text-danger` (for red/negative `#f87171`), `text-accent` (for purple `#7c3aed`), `text-brand` (for cyan `#00d4ff`).
  - For Recharts `stroke`/`fill` props that cannot use CSS classes, reference the `COLORS` constant from `helpers.tsx` instead of inline hex values.
  - Replace `border-[rgba(120,120,200,0.06)]` with a shared CSS variable or Tailwind utility (e.g., `border-line` if defined, or add `--table-border` to `globals.css`).

**Sub-task F6b — ProductSearchBar colors:**
- **`microservce-frontend/app/components/search/ProductSearchBar.tsx`**
  - Replace `text-[#aaa]` with `text-muted`.
  - Replace `bg-[#111128]` with `bg-surface`.
  - Replace `text-[#6868a0]` with `text-muted`.
  - Replace `text-[#c0c0e0]` with `text-ink-light`.
  - Replace `text-[#d0d0e8]` with `text-ink-light`.
  - Replace `stroke="#6868a0"` (SVG) with `stroke="currentColor"` and apply `text-muted` to parent.

**Sub-task F6c — AccessAuditPanel colors:**
- **`microservce-frontend/app/components/admin/access/AccessAuditPanel.tsx`**
  - Replace `rgba(255,255,255,0.02)` and `rgba(0,212,255,0.2)` with theme-appropriate utilities (`bg-white/[0.02]` → `bg-surface-2`, `bg-brand/20`).

---

### F7 — Break down monolithic components (top 5 priority)

**Issue:** Multiple components exceed 300 lines with mixed concerns (data fetching, filtering, rendering, actions).

**F7a — `ProductCatalogPanel.tsx` (474 lines, 56+ props)**
- **`microservce-frontend/app/components/admin/products/ProductCatalogPanel.tsx`**
  - Extract `ProductFiltersBar` — filter inputs, category/vendor selects, apply button.
  - Extract `ProductBulkActions` — select all, bulk delete/price update/category reassign.
  - Extract `ProductTableRow` — individual row rendering with action buttons.
  - Keep `ProductCatalogPanel` as a thin orchestrator.

**F7b — `PosterSlot.tsx` (395 lines)**
- **`microservce-frontend/app/components/posters/PosterSlot.tsx`**
  - Extract `PosterVariantRenderer` — variant display logic.
  - Extract `PosterAnalyticsOverlay` — analytics display.

**F7c — `PosterLinkTargetEditor.tsx` (373 lines)**
- **`microservce-frontend/app/components/posters/admin/PosterLinkTargetEditor.tsx`**
  - Extract `LinkTypeSelector` — link type dropdown and display.
  - Extract `LinkConfigForm` — configuration fields per link type.

**F7d — `PromotionDetailPanel.tsx` (362 lines)**
- **`microservce-frontend/app/components/promotions/PromotionDetailPanel.tsx`**
  - Extract `PromotionRulesSection` — rules/eligibility display.
  - Extract `PromotionBenefitSection` — benefit type and value display.

**F7e — `ProductEditorPanel.tsx` (348 lines)**
- **`microservce-frontend/app/components/admin/products/ProductEditorPanel.tsx`**
  - Extract `ProductSpecsEditor` — specification key-value editing.
  - Extract `ProductVariationsEditor` — variation management.

---

### F8 — Add empty state displays to admin tables

**Issue:** Admin list views show nothing when data is empty — no "No results" message, confusing users.

- **`microservce-frontend/app/components/admin/orders/OrdersTable.tsx`**
  - Add an empty state row: "No orders match the current filters" when `rows.length === 0`.

- **`microservce-frontend/app/components/admin/vendors/VendorListPanel.tsx`**
  - Add empty state: "No vendors found" when the list is empty.

- **`microservce-frontend/app/components/admin/products/ProductCatalogPanel.tsx`**
  - Add empty state: "No products match the current filters" when `rows.length === 0`.

- **`microservce-frontend/app/components/admin/promotions/PromotionsList.tsx`**
  - Add empty state when promotions list is empty.

- Consider creating a shared `EmptyState` component in `microservce-frontend/app/components/ui/` that accepts an icon, title, and description — then reuse across all tables.

---

## Summary

| ID | Area | Priority | Description |
|----|------|----------|-------------|
| B1 | Backend / Order DTO | HIGH | Add `vendorName` to `VendorOrderResponse` |
| B2 | Backend / Transactions | HIGH | Add class-level `@Transactional` to StockService, PaymentService, RefundService |
| B3 | Backend / Transactions | MEDIUM | Add `@Transactional(readOnly=true)` to OrderService read methods |
| B4 | Backend / Exceptions | LOW | Replace raw RuntimeException in OrderController CSV export |
| B5 | Backend / Caching | MEDIUM | Evaluate/document category cache eviction strategy |
| B6 | Backend / JPA | MEDIUM | Add `@EntityGraph` to VendorUserRepository |
| F1 | Frontend / Parity | HIGH | Remove extra CartItem fields not in backend |
| F2 | Frontend / Parity | LOW | Add `stackingGroup` to PublicPromotion type |
| F3 | Frontend / UX | HIGH | Replace UUID text inputs with SearchableSelect |
| F4 | Frontend / Reliability | HIGH | Add `error.tsx` boundaries for route segments |
| F5 | Frontend / UX | MEDIUM | Add `loading.tsx` skeleton files |
| F6 | Frontend / Consistency | MEDIUM | Extract hardcoded colors to theme tokens |
| F7 | Frontend / Maintainability | MEDIUM | Break down 5 monolithic components |
| F8 | Frontend / UX | MEDIUM | Add empty state displays to admin tables |
