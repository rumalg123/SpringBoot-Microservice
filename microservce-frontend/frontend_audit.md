# Frontend Audit — Rumal Store (Next.js E-Commerce Platform)

> Generated: 2026-02-26
> Scope: Full — Refactoring + Missing API Integrations + SSR Migration + Zustand Introduction
> Currency: USD (hardcoded)

---

## Table of Contents

1. [P0 — Foundation & Infrastructure](#p0--foundation--infrastructure)
2. [P1 — Component Architecture (Break Monoliths)](#p1--component-architecture-break-monoliths)
3. [P2 — API Contract Alignment & Missing Integrations](#p2--api-contract-alignment--missing-integrations)
4. [P3 — UI/UX, Theming & Accessibility](#p3--uiux-theming--accessibility)
5. [P4 — Performance & SSR Migration](#p4--performance--ssr-migration)
6. [P5 — React/Next.js Anti-Patterns & Code Quality](#p5--reactnextjs-anti-patterns--code-quality)
7. [P6 — New Pages & Features (Missing Backend Integrations)](#p6--new-pages--features-missing-backend-integrations)

---

## P0 — Foundation & Infrastructure

### 0.1 — Centralize All TypeScript Types into `lib/types/`

**Issue:** 50+ type definitions are scattered across page components (cart, orders, profile, products, wishlist, admin pages). Many are duplicated (e.g., `WishlistItem` defined in 3 files, `Category` in 4 files).

**Files to create:**
- `lib/types/product.ts` — `ProductSummary`, `ProductDetail`, `Variation`, `VariationSummary`, `VariationAttribute`, `UpsertProductRequest`
- `lib/types/cart.ts` — `CartItem`, `CartResponse`, `AddCartItemRequest`, `UpdateCartItemRequest`, `CheckoutPreviewRequest`, `CheckoutPreviewResponse`, `CheckoutResponse`
- `lib/types/order.ts` — `Order`, `OrderItem`, `OrderAddress`, `OrderStatusAudit`, `VendorOrder`, `VendorOrderStatusAudit`, `CreateMyOrderRequest`, `UpdateOrderStatusRequest`, `SetTrackingInfoRequest`, `InvoiceResponse`
- `lib/types/customer.ts` — `Customer`, `CustomerAddress`, `CustomerAddressRequest`, `CommunicationPreferences`, `ActivityLogEntry`, `LinkedAccounts`
- `lib/types/wishlist.ts` — `WishlistItem`, `WishlistResponse`, `WishlistCollection`, `SharedWishlist`, `AddWishlistItemRequest`
- `lib/types/category.ts` — `Category`, `CategoryResponse`, `UpsertCategoryRequest`
- `lib/types/vendor.ts` — `VendorProfile`, `VendorSummary`, `PublicVendorResponse`, `UpsertVendorRequest`, `VendorOperationalState`, `VendorPayoutConfig`
- `lib/types/promotion.ts` — `PublicPromotion`, `Promotion`, `UpsertPromotionRequest`, `CouponCode`, `CouponReservation`, `SpendTier`
- `lib/types/review.ts` — `Review`, `ReviewSummary`, `CreateReviewRequest`, `VoteRequest`, `ReviewReport`
- `lib/types/inventory.ts` — `StockItem`, `Warehouse`, `StockMovement`, `StockReservation`, `StockAdjustRequest`, `BulkStockImportRequest`
- `lib/types/poster.ts` — `Poster`, `PosterVariant`, `UpsertPosterRequest`, `PosterPlacement`
- `lib/types/payment.ts` — `Payment`, `PaymentDetail`, `RefundRequest`, `VendorPayout`, `VendorBankAccount`
- `lib/types/admin.ts` — `DashboardSummary`, `PlatformStaffAccess`, `VendorStaffAccess`, `PermissionGroup`, `AccessAuditEntry`, `ApiKey`, `ActiveSession`, `SystemConfig`, `FeatureFlag`
- `lib/types/search.ts` — Move existing `SearchResponse`, `SearchHit`, `FacetGroup`, `AutocompleteResponse` from `lib/types.ts`
- `lib/types/pagination.ts` — `PagedResponse<T>` with proper Spring Data Page mapping, `PaginationParams`
- `lib/types/analytics.ts` — `VendorDashboardData`, `CustomerInsights`, `AdminDashboardData`
- `lib/types/index.ts` — Barrel export for all types

**Files to change:**
- `lib/types.ts` → Remove, replace with `lib/types/index.ts` barrel
- `app/page.tsx` → Remove inline types, import from `lib/types`
- `app/products/page.tsx` → Remove `WishlistItem`, `WishlistResponse`, `Category`, `SortKey` (lines 20-43)
- `app/products/[id]/page.tsx` → Remove `Variation`, `ProductDetail`, `VariationSummary`, `WishlistItem`, `WishlistResponse` (lines 17-28)
- `app/cart/page.tsx` → Remove `CartItem`, `CartResponse`, `CustomerAddress`, `AppliedPromotion`, `CheckoutPreviewResponse`, `CheckoutResponse` (lines 17-100)
- `app/orders/page.tsx` → Remove `Order`, `OrderItem`, `OrderAddress` (lines 14-35)
- `app/wishlist/page.tsx` → Remove local types
- `app/profile/page.tsx` → Remove local types
- `app/profile/insights/page.tsx` → Remove `CustomerInsights` type
- `app/categories/page.tsx` → Remove `Category` type (lines 9-15)
- `app/categories/[name]/page.tsx` → Remove local types (lines 21-43)
- `app/promotions/page.tsx` → Remove `SpendTier`, `PublicPromotion`, `PageResponse` (lines 13-45)
- `app/admin/dashboard/page.tsx` → Remove 15+ inline types
- `app/admin/orders/page.tsx` → Remove inline types from hooks
- `app/admin/products/page.tsx` → Remove 10+ inline types
- `app/admin/vendors/page.tsx` → Remove vendor types from hooks
- `app/admin/promotions/page.tsx` → Remove all enum/type definitions
- `app/admin/posters/page.tsx` → Remove poster types
- `app/admin/permission-groups/page.tsx` → Remove `PermissionGroup`, `PageResponse` types
- `app/admin/access-audit/page.tsx` → Remove local types
- `app/vendor/settings/page.tsx` → Remove vendor types
- `app/vendor/orders/page.tsx` → Remove order types
- `app/vendor/analytics/page.tsx` → Remove analytics types
- `app/components/admin/orders/useAdminOrders.ts` → Import types
- `app/components/admin/vendors/useAdminVendors.ts` → Import types
- All other component files referencing local types

### 0.2 — Fix `PagedResponse<T>` to Match Spring Data Page Format

**Issue:** Frontend `PagedResponse<T>` in `lib/types.ts` (lines 67-79) uses an inconsistent structure. Some code reads `res.data.content`, others read `res.data.page.totalPages`. The Spring Boot backend returns a consistent Page object.

**Changes:**
- `lib/types/pagination.ts`:
  ```ts
  export type PagedResponse<T> = {
    content: T[];
    totalElements: number;
    totalPages: number;
    number: number;       // current page (0-indexed)
    size: number;
    first: boolean;
    last: boolean;
    empty: boolean;
  };
  ```
- Update all page files that destructure paged responses to use consistent field names

### 0.3 — Introduce Zustand for Global State Management

**Issue:** No global state management. Auth state is in a hook with 17 useState calls. Cart/wishlist use an event bus. Admin hooks have 50+ state variables.

**Files to create:**
- `lib/stores/auth.ts` — Auth store: session status, user profile, roles, capabilities, api client, login/logout/signup methods
- `lib/stores/cart.ts` — Cart store: items, count, loading, fetchCart, addItem, updateItem, removeItem, clearCart
- `lib/stores/wishlist.ts` — Wishlist store: items, collections, count, loading, fetchWishlist, addItem, removeItem, toggleItem
- `lib/stores/ui.ts` — UI store: mobile menu open, cart dropdown open, wishlist dropdown open, scroll position

**Files to change:**
- `lib/authSession.ts` → Refactor into Zustand store (`lib/stores/auth.ts`). Keep `useAuthSession()` as a thin wrapper that reads from the store for backwards compatibility.
- `lib/navEvents.ts` → Replace event bus with Zustand subscriptions. Cart/wishlist stores auto-notify subscribers.
- `app/components/CartNavWidget.tsx` → Replace useState + event listener with `useCartStore()`
- `app/components/WishlistNavWidget.tsx` → Replace useState + event listener with `useWishlistStore()`
- `app/components/AppNav.tsx` → Read auth/cart/wishlist counts from stores instead of prop drilling
- `app/page.tsx` → Replace prop drilling of `emailVerified`, `apiClient` with store access
- `app/cart/page.tsx` → Use cart store for state
- `app/wishlist/page.tsx` → Use wishlist store for state
- `package.json` → Add `zustand` dependency

### 0.4 — Create Shared Constants File

**Issue:** Hardcoded values repeated across files: API base URL, page sizes, status colors, order statuses, free shipping threshold.

**Files to create:**
- `lib/constants.ts`:
  - `API_BASE` (from env)
  - `PAGE_SIZE_DEFAULT = 20`
  - `PAGE_SIZE_SMALL = 8`
  - `PAGE_SIZE_LARGE = 50`
  - `FREE_SHIPPING_THRESHOLD = 25`
  - `ORDER_STATUS_COLORS` map
  - `VENDOR_ORDER_STATUSES` array
  - `PRODUCT_SORT_OPTIONS` array
  - `ADMIN_PERMISSION_OPTIONS` (platform + vendor)
  - `IDEMPOTENCY_WINDOW_MS = 15_000`

**Files to change:**
- `app/page.tsx` → Replace hardcoded "$25+" and stats
- `app/products/page.tsx` → Replace `PAGE_SIZE`, `AGGREGATE_PAGE_SIZE`, `AGGREGATE_MAX_PAGES` (lines 40-42)
- `app/categories/[name]/page.tsx` → Replace duplicate page size constants (lines 41-43)
- `app/orders/page.tsx` → Replace hardcoded status colors (lines 46-55)
- `app/promotions/page.tsx` → Replace `PAGE_SIZE = 20` (line 50)
- `app/admin/vendor-staff/page.tsx` → Replace `VENDOR_PERMISSION_OPTIONS`
- `app/admin/platform-staff/page.tsx` → Replace platform permission options
- `app/cart/page.tsx` → Replace "$25+" free shipping text (line 408)
- `lib/apiClient.ts` → Import `IDEMPOTENCY_WINDOW_MS` instead of hardcoded 15000

### 0.5 — Expand `lib/format.ts` Utility Functions

**Issue:** `money()` function (9-line file) is duplicated in `CartNavWidget.tsx` (line 21), `WishlistNavWidget.tsx` (line 21), `orders/page.tsx` (line 42), `cart/page.tsx` (line 107). Missing common formatters.

**Changes to `lib/format.ts`:**
- Keep `money()` and `calcDiscount()` (already there)
- Add `formatDate(date: string | Date): string` — locale date formatting
- Add `formatDateTime(date: string | Date): string` — locale date+time formatting
- Add `formatRelativeTime(date: string | Date): string` — "2 hours ago"
- Add `truncateText(text: string, max: number): string` — text truncation
- Add `formatCount(n: number): string` — "1.2K", "3.4M" abbreviations
- Add `formatPercentage(value: number, decimals?: number): string`

**Files to change:**
- `app/components/CartNavWidget.tsx` → Remove local `money()` (line 21-23), import from `lib/format`
- `app/components/WishlistNavWidget.tsx` → Remove local `money()` (line 21-24), import from `lib/format`
- `app/orders/page.tsx` → Remove local `money()` (line 42-44), import from `lib/format`
- `app/cart/page.tsx` → Remove local `money()` (line 107-109), import from `lib/format`
- `app/promotions/page.tsx` → Use `formatDate()` instead of inline date formatting
- `app/admin/dashboard/page.tsx` → Use `formatCount()` for large numbers
- `app/profile/insights/page.tsx` → Use `formatDate()` and `formatCount()`

### 0.6 — Expand `lib/error.ts` Error Handling

**Issue:** 16-line file with only `getErrorMessage()`. Silent catch blocks everywhere (~30 locations). No error type discrimination.

**Changes to `lib/error.ts`:**
- Add `isNetworkError(err: unknown): boolean`
- Add `isTimeoutError(err: unknown): boolean`
- Add `isValidationError(err: unknown): boolean`
- Add `getValidationErrors(err: unknown): Record<string, string>`
- Add `handleApiError(err: unknown, fallback?: string): string` — improved version with error type awareness

**Files to change (add proper error handling):**
- `app/page.tsx` lines 56-70 → Show toast on product fetch error
- `app/products/page.tsx` lines 303-307 → Show error message instead of silent fail
- `app/products/[id]/page.tsx` lines 73-88 → Distinguish 404 vs network error
- `app/cart/page.tsx` lines 196-199, 269-273 → Show specific error messages
- `app/orders/page.tsx` line 108, 134 → Distinguish validation vs server errors
- `app/wishlist/page.tsx` → Add error handling for all API calls
- `app/categories/page.tsx` lines 132-144 → Show user notification
- `app/promotions/page.tsx` lines 284-286, 313 → Show error messages
- `lib/personalization.ts` lines 58-65 → Add logging instead of empty catch

---

## P1 — Component Architecture (Break Monoliths)

### 1.1 — Break Down `app/page.tsx` (Home Page — 746 lines)

**Issue:** Single component handles hero, trust bar, flash deals, product grids, recommendations, CTA banner, category showcase.

**Extract into:**
- `app/components/home/HeroSection.tsx` — Hero banner with search and CTA buttons
- `app/components/home/TrustBar.tsx` — Trust icons bar (free shipping, secure payment, etc.)
- `app/components/home/FlashDealsSection.tsx` — Flash deals carousel/grid with countdown
- `app/components/home/ProductGridSection.tsx` — Reusable product grid with title (used for "New Arrivals", "Just For You", etc.)
- `app/components/home/CTABanner.tsx` — Sign-up CTA banner
- `app/components/home/CategoryShowcase.tsx` — Category cards showcase

**Changes to `app/page.tsx`:**
- Reduce to ~100 lines: data fetching + composition of sub-components
- Move trust icon SVGs to `app/components/icons/TrustIcons.tsx`
- Move product card rendering to shared `ProductCard` component (see 1.7)

### 1.2 — Break Down `app/products/page.tsx` (Product Catalog — 678 lines)

**Issue:** Filtering, sorting, pagination, wishlist toggle, product rendering all in one component with 17 useState calls.

**Extract into:**
- `app/components/catalog/ProductGrid.tsx` — Grid of ProductCard components with loading states
- `app/components/catalog/CatalogPagination.tsx` — Page navigation (already have `ui/Pagination.tsx` — reuse it)
- `lib/hooks/useProductCatalog.ts` — Custom hook encapsulating filter state, URL sync, and data fetching
- `lib/hooks/useWishlistToggle.ts` — Reusable hook for wishlist add/remove (duplicated in 3 pages)

**Changes to `app/products/page.tsx`:**
- Reduce to ~150 lines: hook consumption + layout composition
- Replace 17 useState calls with `useProductCatalog()` hook
- Use `useWishlistToggle()` instead of inline wishlist logic

### 1.3 — Break Down `app/products/[id]/page.tsx` (Product Detail — 720 lines)

**Issue:** Product loading, variation selection, image gallery, cart/wishlist operations, reviews, recommendations all in one component with 15 useState calls.

**Extract into:**
- `app/components/product/ProductImageGallery.tsx` — Image carousel with thumbnails
- `app/components/product/ProductInfo.tsx` — Name, price, discount, description, specs
- `app/components/product/VariationSelector.tsx` — Variation attribute selectors
- `app/components/product/PurchasePanel.tsx` — Quantity selector, Add to Cart, Buy Now buttons
- `app/components/product/SimilarProducts.tsx` — Similar products carousel
- `app/components/product/BoughtTogether.tsx` — Frequently bought together section
- `lib/hooks/useProductDetail.ts` — Custom hook for product + variation state

**Changes to `app/products/[id]/page.tsx`:**
- Reduce to ~120 lines: hook + composition

### 1.4 — Break Down `app/cart/page.tsx` (Cart — 726 lines)

**Issue:** Cart items, checkout form, address selection, payment, suggestions all in one component with 16 useState calls.

**Extract into:**
- `app/components/cart/CartItemList.tsx` — List of cart items with quantity controls
- `app/components/cart/CartSummary.tsx` — Order summary (subtotal, shipping, discounts, total)
- `app/components/cart/CheckoutPanel.tsx` — Address selection + checkout button
- `app/components/cart/AddressSelector.tsx` — Address picker (extract from profile too)
- `app/components/cart/SavedForLater.tsx` — Saved items section (NEW — uses backend save-for-later)
- `app/components/cart/CartSuggestions.tsx` — Product suggestions based on cart contents

**Changes to `app/cart/page.tsx`:**
- Reduce to ~100 lines
- Use cart Zustand store for state

### 1.5 — Break Down `app/admin/dashboard/page.tsx` (Admin Dashboard — ~770 lines)

**Issue:** 15+ chart types, 15+ inline type definitions, 400+ lines of inline styles.

**Extract into:**
- `app/components/admin/dashboard/DashboardOverviewCards.tsx` — KPI summary cards
- `app/components/admin/dashboard/RevenueChart.tsx` — Revenue trend line chart
- `app/components/admin/dashboard/OrdersChart.tsx` — Orders bar chart
- `app/components/admin/dashboard/TopProductsTable.tsx` — Top selling products table
- `app/components/admin/dashboard/CustomersChart.tsx` — Customer acquisition chart
- `app/components/admin/dashboard/InventoryStatusChart.tsx` — Stock status pie chart
- `lib/hooks/useAdminDashboard.ts` — Dashboard data fetching hook

### 1.6 — Break Down `app/admin/products/page.tsx` (Admin Products — ~2500 lines)

**Issue:** Product CRUD, image uploads with drag-drop, variations editor, bulk operations, CSV import — all in one file.

**Already partially extracted but still too large. Further extract:**
- `app/components/admin/products/ProductFilters.tsx` — Filter controls for product list
- `app/components/admin/products/BulkOperationsPanel.tsx` — Bulk delete/price/category operations
- `app/components/admin/products/CsvImportExport.tsx` — Import/export functionality
- `lib/hooks/useProductAdmin.ts` — Product admin state management

### 1.7 — Create Shared `ProductCard` Component

**Issue:** Product card rendering logic is duplicated in: `app/page.tsx` (lines 331-338, 547-588), `app/products/page.tsx` (lines 524-617), `app/categories/[name]/page.tsx`, `app/promotions/page.tsx`.

**Files to create:**
- `app/components/catalog/ProductCard.tsx` — Shared product card with:
  - Image with hover overlay
  - Product name, price, discount badge
  - Wishlist toggle button
  - Add to cart button
  - Rating stars (if available)
  - Props: `product: ProductSummary`, `isWishlisted?: boolean`, `onWishlistToggle?: () => void`, `onAddToCart?: () => void`

**Files to change:**
- `app/page.tsx` → Replace all inline product card JSX with `<ProductCard />`
- `app/products/page.tsx` → Replace product card JSX with `<ProductCard />`
- `app/categories/[name]/page.tsx` → Replace product card JSX with `<ProductCard />`
- `app/promotions/page.tsx` → Replace product card JSX with `<ProductCard />`

### 1.8 — Break Down `app/admin/posters/page.tsx` (~2500 lines)

**Extract into:**
- `app/components/admin/posters/PosterList.tsx` — Poster listing with filters
- `app/components/admin/posters/PosterEditor.tsx` — Create/edit form
- `app/components/admin/posters/PosterAnalytics.tsx` — Click/impression stats
- `app/components/admin/posters/PosterScheduler.tsx` — Scheduling controls
- `app/components/admin/posters/PosterVariantEditor.tsx` — A/B variant management
- `lib/hooks/usePosterAdmin.ts` — Poster admin state

### 1.9 — Break Down `app/admin/promotions/page.tsx` (~2500 lines)

**Extract into:**
- `app/components/admin/promotions/PromotionList.tsx` — Promotion listing with filters
- `app/components/admin/promotions/PromotionEditor.tsx` — Multi-step promotion form
- `app/components/admin/promotions/BenefitConfigPanel.tsx` — Benefit type configuration
- `app/components/admin/promotions/TierEditor.tsx` — Spend tier configuration
- `app/components/admin/promotions/CouponManager.tsx` — Coupon code generation & management
- `lib/hooks/usePromotionAdmin.ts` — Promotion admin state

### 1.10 — Break Down `app/promotions/page.tsx` (Customer Promotions — 847 lines)

**Extract into:**
- `app/components/promotions/PromotionCard.tsx` — Individual promotion display
- `app/components/promotions/FlashSalesBanner.tsx` — Flash sales section with countdown
- `app/components/promotions/PromotionFilters.tsx` — Search and filter controls
- `app/components/promotions/CouponClaimButton.tsx` — Coupon reservation button
- `lib/hooks/useCountdown.ts` — Reusable countdown hook (extracted from inline logic)

### 1.11 — Break Down `app/profile/page.tsx` (Customer Profile)

**Extract into:**
- `app/components/profile/ProfileHeader.tsx` — Avatar, name, email, loyalty tier
- `app/components/profile/ProfileEditor.tsx` — Edit profile form
- `app/components/profile/AddressManager.tsx` — Address CRUD (reuse in cart checkout)
- `app/components/profile/SecuritySettings.tsx` — Password change, linked accounts
- `app/components/profile/CommunicationPreferences.tsx` — Email/notification preferences (NEW)

### 1.12 — Break Down `app/orders/page.tsx` (Customer Orders — 630 lines)

**Extract into:**
- `app/components/orders/OrderList.tsx` — Orders list with status filter
- `app/components/orders/OrderDetailPanel.tsx` — Single order detail view
- `app/components/orders/OrderStatusTimeline.tsx` — Status history timeline
- `app/components/orders/OrderActions.tsx` — Cancel, reorder actions

### 1.13 — Break Down `app/vendor/settings/page.tsx` (Vendor Settings — 817 lines)

**Extract into:**
- `app/components/vendor/settings/VendorProfileTab.tsx` — Business profile form
- `app/components/vendor/settings/VendorPayoutTab.tsx` — Payout configuration
- `app/components/vendor/settings/VendorActionsTab.tsx` — Verification, stop/resume orders

### 1.14 — Refactor `app/components/AppNav.tsx` (416 lines)

**Issue:** 20+ props passed, 240+ lines of nested conditionals, complex admin menu rendering.

**Extract into:**
- `app/components/nav/NavLogo.tsx` — Logo component
- `app/components/nav/NavLinks.tsx` — Main navigation links
- `app/components/nav/NavAdminMenu.tsx` — Admin dropdown menu
- `app/components/nav/NavVendorMenu.tsx` — Vendor dropdown menu
- `app/components/nav/NavUserMenu.tsx` — User account dropdown
- `app/components/nav/MobileMenu.tsx` — Mobile hamburger menu

**Changes to `app/components/AppNav.tsx`:**
- Reduce to ~80 lines: read from auth/cart/wishlist stores, compose sub-components
- Remove all prop drilling — sub-components read from Zustand stores

---

## P2 — API Contract Alignment & Missing Integrations

### 2.1 — Fix Cart API Request/Response Bodies

**Issue:** Frontend cart types may not match backend `CartResponse` exactly.

**Backend `CartResponse`:**
```
{
  id, customerId, items: [{ id, productId, productName, productSlug, mainImage, sku,
    unitPrice, quantity, subtotal, variationAttributes: [{name, value}],
    savedForLater }],
  itemCount, subtotal, note, createdAt, updatedAt
}
```

**Backend `AddCartItemRequest`:**
```
{ productId (required), quantity (default 1) }
```

**Backend `UpdateCartItemRequest`:**
```
{ quantity (required, min 1) }
```

**Files to verify/fix:**
- `lib/types/cart.ts` — Ensure exact field match
- `app/cart/page.tsx` — Verify request bodies in add/update/remove calls
- `app/components/CartNavWidget.tsx` — Verify response destructuring

### 2.2 — Fix Wishlist API Request/Response Bodies

**Issue:** Backend supports collections and sharing. Frontend only uses flat item list.

**Backend `WishlistItemResponse`:**
```
{ id, customerId, productId, productName, productSlug, mainImage, sku,
  currentPrice, addedPrice, priceDropped, note, collectionId, addedAt }
```

**Backend `AddWishlistItemRequest`:**
```
{ productId (required), note (optional), collectionId (optional) }
```

**Files to fix:**
- `lib/types/wishlist.ts` — Add `collectionId`, `addedPrice`, `priceDropped` fields
- `app/wishlist/page.tsx` — Update to handle new fields (price drop alerts)

### 2.3 — Fix Order API Mapping

**Issue:** Frontend uses `/orders/me` but type mapping may be incomplete. Backend returns `OrderDetailsResponse` with vendor sub-orders.

**Backend `OrderResponse`:**
```
{ id, orderNumber, customerId, customerEmail, customerName,
  status, subtotal, shippingAmount, discountAmount, totalAmount,
  shippingAddress: { ... }, billingAddress: { ... },
  paymentMethod, paymentStatus, adminNote,
  itemCount, vendorOrderCount, createdAt, updatedAt }
```

**Backend `OrderDetailsResponse` (extends OrderResponse):**
```
{ ...OrderResponse, items: [{ id, productId, productName, sku, quantity,
  unitPrice, subtotal, variationAttributes, vendorId, vendorName }],
  vendorOrders: [{ id, vendorId, vendorName, status, items, subtotal,
  trackingNumber, trackingUrl, shippingCarrier }] }
```

**Files to fix:**
- `lib/types/order.ts` — Match exactly
- `app/orders/page.tsx` — Use `OrderDetailsResponse` for detail view
- `app/components/orders/OrderDetailPanel.tsx` — Show vendor sub-order tracking

### 2.4 — Fix Search API Mapping

**Issue:** Frontend `SearchResponse` in `lib/types.ts` generally matches, but verify facet handling.

**Backend `SearchResponse`:**
```
{ content: SearchHit[], facets: [{ name, buckets: [{ key, docCount }] }],
  page, size, totalElements, totalPages, query, tookMs }
```

**Files to verify:**
- `lib/types/search.ts` — Ensure facets are properly typed
- `app/products/page.tsx` — Verify facet-based filtering actually uses backend facets

### 2.5 — Add Missing Cart "Save for Later" Integration

**Issue:** Backend supports `POST /cart/me/items/{itemId}/save-for-later` and `POST /cart/me/items/{itemId}/move-to-cart` but frontend doesn't use them.

**Files to create:**
- `app/components/cart/SavedForLater.tsx` — Display saved items with "Move to Cart" button

**Files to change:**
- `app/cart/page.tsx` → Add "Save for Later" button on each cart item
- `lib/types/cart.ts` → Add `savedForLater: boolean` to `CartItem`

### 2.6 — Add Order Invoice Integration

**Issue:** Backend supports `GET /orders/me/{id}/invoice` but frontend doesn't use it.

**Files to change:**
- `app/components/orders/OrderDetailPanel.tsx` → Add "Download Invoice" button
- Call `GET /orders/me/{orderId}/invoice` and render/download PDF

### 2.7 — Add Order Cancellation Integration

**Issue:** Backend supports `POST /orders/me/{id}/cancel` with optional `CancelOrderRequest`. Frontend may not have cancel button.

**Files to change:**
- `app/components/orders/OrderActions.tsx` → Add "Cancel Order" with confirmation modal and optional reason

### 2.8 — Add Customer Communication Preferences Integration

**Issue:** Backend has `GET/PUT /customers/me/communication-preferences` but no frontend page.

**Files to create:**
- `app/components/profile/CommunicationPreferences.tsx` — Toggle email notifications, marketing, order updates

**Files to change:**
- `app/profile/page.tsx` → Add Communication Preferences tab/section

### 2.9 — Add Customer Activity Log Integration

**Issue:** Backend has `GET /customers/me/activity-log` but no frontend display.

**Files to change:**
- `app/profile/insights/page.tsx` → Add activity log timeline section

### 2.10 — Add Customer Linked Accounts Integration

**Issue:** Backend has `GET /customers/me/linked-accounts` but no frontend display.

**Files to change:**
- `app/components/profile/SecuritySettings.tsx` → Show linked social accounts (Google, Facebook, etc.)

### 2.11 — Add Product View Tracking Integration

**Issue:** Backend has `POST /products/{id}/view` but frontend may not call it consistently. Also personalization tracking exists separately.

**Files to verify:**
- `app/products/[id]/page.tsx` → Ensure `POST /products/{id}/view` is called on page load (distinct from personalization tracking)

### 2.12 — Add Trending Products Integration

**Issue:** Backend has `GET /personalization/trending` but frontend doesn't use it.

**Files to change:**
- `app/page.tsx` → Add "Trending Now" section using trending endpoint
- `lib/personalization.ts` → Add `fetchTrending()` function

### 2.13 — Fix Review Image Upload

**Issue:** Backend supports review images via `GET /reviews/images/{path}`. Verify frontend `ReviewForm.tsx` supports image upload.

**Files to verify/fix:**
- `app/components/reviews/ReviewForm.tsx` → Ensure image upload capability
- `lib/image.ts` → Ensure review image URL resolution works

### 2.14 — Add Review Voting Integration

**Issue:** Backend has `POST /reviews/{reviewId}/vote` for helpfulness voting.

**Files to verify:**
- `app/components/reviews/ReviewCard.tsx` → Should have "Helpful" / "Not Helpful" buttons

### 2.15 — Add Review Reporting Integration

**Issue:** Backend has `POST /reviews/{reviewId}/report` for flagging inappropriate reviews.

**Files to change:**
- `app/components/reviews/ReviewCard.tsx` → Add "Report" button with reason modal

### 2.16 — Fix Promotion Coupon Reservation Flow

**Issue:** Backend has `POST /promotions/me/reserve-coupon` and `POST /promotions/me/release-coupon`. Verify frontend uses them correctly.

**Files to verify:**
- `app/promotions/page.tsx` → Ensure coupon claim sends `CreateCouponReservationRequest`

### 2.17 — Add `apiClient` Timeout Configuration

**Issue:** `lib/apiClient.ts` creates Axios instance without timeout. Backend has rate limiting but client can hang forever.

**Files to change:**
- `lib/apiClient.ts` → Add `timeout: 30000` (30s) to Axios config
- Add request cancellation support (AbortController)

### 2.18 — Fix Idempotency Key Memory Leak

**Issue:** `lib/apiClient.ts` (lines 53-74) stores idempotency keys in a Map without any size limit or TTL enforcement. In long sessions, this grows unbounded.

**Files to change:**
- `lib/apiClient.ts` → Add max cache size (e.g., 100 entries) with LRU eviction, or use TTL-based cleanup

---

## P3 — UI/UX, Theming & Accessibility

### 3.1 — Extract Hardcoded Colors to CSS Variables

**Issue:** Multiple components use hardcoded colors instead of the CSS custom properties defined in `globals.css`.

**Files to fix:**
- `app/orders/page.tsx` lines 46-55 → Replace `"#10b981"`, `"#f59e0b"`, `"#3b82f6"`, `"#ef4444"` with `var(--success)`, `var(--warning)`, `var(--brand)`, `var(--danger)`
- `app/promotions/page.tsx` lines 87-102 → Replace hardcoded color mappings with theme tokens
- `app/profile/insights/page.tsx` lines 37-43 → Replace tier color hardcodes with CSS variables
- `app/admin/dashboard/page.tsx` → Replace all inline chart colors with theme tokens
- `app/vendor/analytics/page.tsx` → Replace chart color hardcodes
- `app/admin/access-audit/page.tsx` → Replace action badge colors

### 3.2 — Move Category SVG Icons to Reusable Component

**Issue:** `app/categories/page.tsx` lines 18-99 has 11 hardcoded category icon SVGs. Similar SVGs scattered across footer, navigation, etc.

**Files to create:**
- `app/components/icons/CategoryIcons.tsx` — Named exports for each category icon
- `app/components/icons/SocialIcons.tsx` — Social media icons (from Footer)
- `app/components/icons/TrustIcons.tsx` — Trust bar icons (from homepage)
- `app/components/icons/index.ts` — Barrel export

**Files to change:**
- `app/categories/page.tsx` → Import from icons library
- `app/components/Footer.tsx` → Import social icons
- `app/page.tsx` → Import trust icons

### 3.3 — Standardize Loading States

**Issue:** Loading states are inconsistent across pages. Some use spinner, some use skeleton, some show nothing.

**Files to create:**
- `app/components/ui/PageSkeleton.tsx` — Full page skeleton (header + content area)
- `app/components/ui/CardSkeleton.tsx` — Product card skeleton
- `app/components/ui/TableSkeleton.tsx` — Data table skeleton
- `app/components/ui/FormSkeleton.tsx` — Form skeleton

**Files to change (standardize loading):**
- `app/page.tsx` → Use `CardSkeleton` grid during product load
- `app/products/page.tsx` → Use `CardSkeleton` grid
- `app/categories/page.tsx` → Use `CardSkeleton` for category cards
- `app/admin/dashboard/page.tsx` → Use chart skeleton
- All admin pages → Use `TableSkeleton` for data tables

### 3.4 — Add Proper Error Boundaries

**Issue:** No error boundaries anywhere. A single component crash takes down the whole page.

**Files to create:**
- `app/components/ErrorBoundary.tsx` — React error boundary with retry button
- `app/components/ApiErrorFallback.tsx` — Error state for API failures (retry + message)

**Files to change:**
- `app/layout.tsx` → Wrap children in top-level ErrorBoundary
- `app/page.tsx` → Wrap each section in ErrorBoundary
- All admin pages → Wrap data panels in ErrorBoundary

### 3.5 — Accessibility Audit

**Issue:** Multiple accessibility gaps across the application.

**Files to fix:**
- `app/categories/page.tsx` → Add `aria-label` to SVG icons, add `alt` text descriptions
- `app/components/AppNav.tsx` → Add `aria-expanded` to dropdowns, `aria-current="page"` to active links
- `app/components/CartNavWidget.tsx` → Add descriptive `aria-label` (line 118)
- `app/components/WishlistNavWidget.tsx` → Add descriptive `aria-label` (line 90)
- `app/components/CategoryMenu.tsx` → Add `role="menu"` and `role="menuitem"` attributes
- `app/orders/page.tsx` → Add `<label>` elements for form inputs (lines 391-450)
- All modals → Add `role="dialog"`, `aria-modal="true"`, focus trapping
- All forms → Add proper `<label>` associations
- All images → Verify `alt` text is meaningful

### 3.6 — Standardize Button Styles

**Issue:** Mix of inline styles and CSS classes for buttons. Some use `.btn-primary`, others use inline `background: "var(--brand)"`.

**Files to fix:**
- Audit all components for inline button styles and replace with `.btn-primary`, `.btn-outline`, `.btn-ghost`, `.btn-danger` from `globals.css`
- `app/components/CartNavWidget.tsx` → Replace inline styles with CSS classes
- `app/components/WishlistNavWidget.tsx` → Replace inline styles with CSS classes
- `app/components/CategoryMenu.tsx` → Replace inline styles with CSS classes

### 3.7 — Standardize Empty States

**Issue:** The `EmptyState` component exists (`app/components/ui/EmptyState.tsx`) but is not used consistently. Some pages have inline empty state JSX.

**Files to change:**
- `app/wishlist/page.tsx` → Use `EmptyState` component
- `app/orders/page.tsx` → Use `EmptyState` component
- `app/cart/page.tsx` → Use `EmptyState` component for empty cart
- All admin list pages → Use `EmptyState` for empty tables

---

## P4 — Performance & SSR Migration

### 4.1 — Migrate Public Pages to Server Components

**Issue:** All pages use `"use client"`. Public-facing pages should leverage Server Components for SEO, faster initial load, and reduced client JS.

**Strategy:** Use server components for data fetching wrappers, client components for interactive parts.

**Pages to convert:**

#### `app/page.tsx` (Home)
- Create `app/page.tsx` as Server Component — fetch featured products, categories, promotions server-side
- Create `app/components/home/HomeClient.tsx` as client component for interactive elements (auth-dependent UI, wishlist toggles)
- Server fetches: `GET /products?sortBy=newest&size=8`, `GET /categories`, `GET /personalization/trending`, `GET /promotions/flash-sales`

#### `app/products/[id]/page.tsx` (Product Detail)
- Create server component wrapper — fetch product data + generateMetadata for SEO
- Keep interactive parts (variation selector, cart actions, reviews) as client components
- Server fetches: `GET /products/{idOrSlug}`, `GET /products/{idOrSlug}/variations`, `GET /reviews/products/{productId}/summary`

#### `app/categories/page.tsx` (Categories)
- Convert to Server Component — categories are mostly static
- Server fetches: `GET /categories`

#### `app/promotions/page.tsx` (Promotions)
- Create server component wrapper for initial promotions list
- Keep countdown timers and coupon claim as client components

### 4.2 — Add `generateMetadata` for SEO

**Issue:** No dynamic meta tags. Only static metadata in `layout.tsx`.

**Files to create/change:**
- `app/products/[id]/page.tsx` → Add `generateMetadata()` for product name, description, image (Open Graph)
- `app/categories/[name]/page.tsx` → Add `generateMetadata()` for category name
- `app/promotions/page.tsx` → Add metadata for promotions page
- `app/page.tsx` → Keep existing static metadata but enhance with Open Graph tags

### 4.3 — Optimize Image Loading

**Issue:** Product images use plain `<img>` tags instead of Next.js `<Image>` component. No lazy loading, no responsive sizes.

**Files to change:**
- Create `app/components/ui/ProductImage.tsx` — Wrapper around Next.js `<Image>` with:
  - Blur placeholder
  - Responsive sizes
  - Error fallback
  - CDN URL construction from `lib/image.ts`
- Update all product card instances to use `ProductImage`
- `next.config.ts` → Add `images.remotePatterns` for product image CDN domain

### 4.4 — Implement Route-Level Code Splitting

**Issue:** Admin and vendor pages bundled with customer-facing code even when not needed.

**Files to change:**
- `app/admin/layout.tsx` → Add admin-specific layout with auth guard (already partially exists, verify lazy loading)
- `app/vendor/layout.tsx` → Add vendor-specific layout with auth guard
- Verify admin components aren't imported in customer pages

### 4.5 — Add Request Deduplication for Personalization Events

**Issue:** `lib/personalization.ts` fires individual fetch calls for every event. No batching or deduplication.

**Files to change:**
- `lib/personalization.ts` → Add event batching (queue events, flush every 2 seconds or on page unload)
- Add `navigator.sendBeacon()` for unload events

### 4.6 — Optimize Re-renders in Admin Tables

**Issue:** Large admin tables (orders, products, vendors) re-render entire table on any state change.

**Files to change:**
- `app/components/ui/DataTable.tsx` → Memoize rows with `React.memo`
- `app/components/admin/orders/OrdersTable.tsx` → Memoize row components
- Use `useMemo` for filtered/sorted data before render

---

## P5 — React/Next.js Anti-Patterns & Code Quality

### 5.1 — Fix Incomplete `useEffect` Dependency Arrays

**Issue:** Multiple useEffect hooks have missing dependencies, risking stale closures and bugs.

**Files to fix:**
- `app/page.tsx` line 78 → Add `session.token` to deps
- `app/products/page.tsx` line 312 → Add `isAuthenticated`, `apiClient` to deps
- `app/products/[id]/page.tsx` line 104 → Add `token` to deps
- `app/categories/[name]/page.tsx` line 369-379 → Verify all filter deps
- `app/profile/insights/page.tsx` line 102-117 → Add `customerId` to deps
- `app/components/AppNav.tsx` line 48 → Add `pathname` to deps

### 5.2 — Replace String-Based Status with Enums/Union Types

**Issue:** Status values use magic strings ("loading", "ready", "error", "idle") across many files.

**Files to create:**
- `lib/types/status.ts`:
  ```ts
  export type LoadingStatus = "idle" | "loading" | "ready" | "error";
  ```

**Files to change:**
- All files using `const [status, setStatus] = useState("idle")` → Use `LoadingStatus` type
- `app/page.tsx`, `app/products/page.tsx`, `app/categories/page.tsx`, `app/promotions/page.tsx`, etc.

### 5.3 — Consolidate Related useState Calls into Objects

**Issue:** Many components have 15-20 separate useState calls for related state. This causes excessive re-renders.

**Examples:**
- `app/products/page.tsx` → 17 useState → Consolidate into `{ filters, pagination, data, ui }` objects or use `useReducer`
- `app/products/[id]/page.tsx` → 15 useState → Consolidate into `{ product, variation, ui }` objects
- `app/cart/page.tsx` → 16 useState → Use cart Zustand store
- `lib/authSession.ts` → 17 useState → Use auth Zustand store

### 5.4 — Fix Race Conditions in Data Fetching

**Issue:** Multiple useEffect hooks fetch data without cancellation. Fast navigation can cause stale data to overwrite fresh data.

**Files to fix:**
- `app/products/page.tsx` → Add `AbortController` to search/filter fetches
- `app/products/[id]/page.tsx` → Add cancellation for product/variation fetches
- `app/categories/[name]/page.tsx` → Add cancellation for category product fetches
- All admin pages → Add `AbortController` to data fetching hooks

**Pattern:**
```ts
useEffect(() => {
  const controller = new AbortController();
  fetchData({ signal: controller.signal });
  return () => controller.abort();
}, [deps]);
```

### 5.5 — Replace Inline API Base URL with Environment Variable

**Issue:** `process.env.NEXT_PUBLIC_API_BASE || "https://gateway.rumalg.me"` repeated in many files.

**Files to fix:**
- `app/page.tsx` line 59 → Use `apiClient` or constant from `lib/constants.ts`
- `app/products/page.tsx` line 135 → Use constant
- `app/categories/[name]/page.tsx` → Use constant
- `app/promotions/page.tsx` line 49 → Use constant
- `lib/personalization.ts` line 1 → Use constant
- All files with `NEXT_PUBLIC_API_BASE` fallback

### 5.6 — Fix SSR/Hydration Safety in `lib/authSession.ts`

**Issue:** Global singletons (`_keycloak`, `_promise`) at module level (lines 9-10) can cause issues in SSR environments.

**Files to change:**
- `lib/authSession.ts` → Guard all Keycloak initialization behind `typeof window !== "undefined"` check
- Ensure `useAuthSession()` returns `idle` status during SSR

### 5.7 — Fix JWT Parsing Without Validation

**Issue:** `lib/authSession.ts` (lines 21-33) parses JWT without validating structure. Malformed tokens can crash the app.

**Files to change:**
- `lib/authSession.ts` → Wrap JWT parsing in try-catch, return null on failure
- Add basic JWT structure validation (3 parts separated by dots)

### 5.8 — Fix Cookie Operations Without SSR Guard

**Issue:** `lib/personalization.ts` (lines 24-31) accesses `document.cookie` without checking for SSR context.

**Files to change:**
- `lib/personalization.ts` → Guard all `document` and `window` access behind `typeof window !== "undefined"`

### 5.9 — Create Custom Hooks for Repeated Patterns

**Issue:** Several patterns are duplicated across multiple components.

**Hooks to create:**
- `lib/hooks/useWishlistToggle.ts` — Wishlist add/remove with optimistic UI (duplicated in products, categories, home)
- `lib/hooks/useDebounce.ts` — Debounced value hook (debounce pattern repeated in search, filters, slug validation)
- `lib/hooks/useCountdown.ts` — Countdown timer (repeated in promotions page and flash deals)
- `lib/hooks/usePagination.ts` — Pagination state + URL sync (repeated in every list page)
- `lib/hooks/useSlugValidation.ts` — Debounced slug availability check (repeated in admin categories, products, posters, vendors)

### 5.10 — Fix Unsafe Type Assertions

**Issue:** Multiple `as` casts without runtime validation.

**Files to fix:**
- `app/products/[id]/page.tsx` line 245 → Validate response before cast
- `app/cart/page.tsx` line 141-142, 245 → Validate response structure
- `app/orders/page.tsx` line 78, 150 → Validate paged response
- `app/components/CartNavWidget.tsx` lines 55-56 → Validate cart response
- `app/profile/insights/page.tsx` lines 108-109 → Validate analytics response

---

## P6 — New Pages & Features (Missing Backend Integrations)

### 6.1 — Wishlist Collections & Sharing

**Backend endpoints:**
- `GET /wishlist/me/collections`
- `POST /wishlist/me/collections`
- `PUT /wishlist/me/collections/{collectionId}`
- `DELETE /wishlist/me/collections/{collectionId}`
- `POST /wishlist/me/collections/{collectionId}/share`
- `DELETE /wishlist/me/collections/{collectionId}/share`
- `GET /wishlist/shared/{shareToken}`

**Files to create:**
- `app/components/wishlist/WishlistCollections.tsx` — Collection tabs/sidebar
- `app/components/wishlist/CollectionForm.tsx` — Create/edit collection modal
- `app/components/wishlist/ShareButton.tsx` — Share collection with link
- `app/wishlist/shared/[token]/page.tsx` — Public shared wishlist page

**Files to change:**
- `app/wishlist/page.tsx` → Integrate collections UI
- `lib/types/wishlist.ts` → Add collection types

### 6.2 — Admin Payment Management

**Backend endpoints:**
- `GET /admin/payments`
- `GET /admin/payments/{id}`
- `POST /admin/payments/{id}/refund`
- `POST /admin/payments/{id}/payout`
- `GET /admin/payments/refunds`
- `POST /admin/payments/refunds/{id}/finalize`

**Files to create:**
- `app/admin/payments/page.tsx` — Payment listing with filters
- `app/components/admin/payments/PaymentList.tsx` — Payment data table
- `app/components/admin/payments/PaymentDetail.tsx` — Payment detail panel
- `app/components/admin/payments/RefundPanel.tsx` — Initiate refund form
- `app/components/admin/payments/PayoutPanel.tsx` — Vendor payout form
- `app/components/admin/payments/RefundsList.tsx` — Refund requests list

**Files to change:**
- `app/components/AppNav.tsx` → Add "Payments" admin nav link

### 6.3 — Admin Review Moderation

**Backend endpoints:**
- `GET /admin/reviews`
- `PATCH /admin/reviews/{reviewId}/status`
- `PATCH /admin/reviews/{reportId}/report-status`

**Files to create:**
- `app/admin/reviews/page.tsx` — Review moderation dashboard
- `app/components/admin/reviews/ReviewModerationList.tsx` — Review listing with status filters
- `app/components/admin/reviews/ReviewReportPanel.tsx` — Review report handling

**Files to change:**
- `app/components/AppNav.tsx` → Add "Reviews" admin nav link

### 6.4 — Admin API Key Management

**Backend endpoints:**
- `GET /admin/access/api-keys`
- `POST /admin/access/api-keys`
- `DELETE /admin/access/api-keys/{id}`

**Files to create:**
- `app/admin/api-keys/page.tsx` — API key management page
- `app/components/admin/access/ApiKeyList.tsx` — Key listing with create/revoke

### 6.5 — Admin Session Management

**Backend endpoints:**
- `GET /admin/access/sessions`
- `DELETE /admin/access/sessions/{id}`

**Files to create:**
- `app/admin/sessions/page.tsx` — Active sessions list
- `app/components/admin/access/SessionList.tsx` — Session table with terminate action

### 6.6 — Admin System Configuration

**Backend endpoints:**
- `GET /admin/system/config`
- `PUT /admin/system/config/{key}`
- `GET /admin/system/feature-flags`
- `PUT /admin/system/feature-flags/{flag}`

**Files to create:**
- `app/admin/settings/page.tsx` — System configuration page
- `app/components/admin/settings/SystemConfigPanel.tsx` — Config key-value editor
- `app/components/admin/settings/FeatureFlagPanel.tsx` — Feature flag toggles

### 6.7 — Vendor Review Management

**Backend endpoints:**
- `GET /reviews/vendor/me`
- `POST /reviews/{reviewId}/vendor/reply`
- `PUT /reviews/{reviewId}/vendor/reply`

**Files to create:**
- `app/vendor/reviews/page.tsx` — Vendor review listing
- `app/components/vendor/reviews/VendorReviewList.tsx` — Reviews for vendor's products
- `app/components/vendor/reviews/VendorReplyForm.tsx` — Reply to customer reviews

### 6.8 — Customer Payment History

**Backend endpoints:**
- `GET /payments/me/{paymentId}`
- `GET /payments/me/order/{orderId}`

**Files to change:**
- `app/components/orders/OrderDetailPanel.tsx` → Show payment status and details for each order

### 6.9 — Vendor Bank Account Management

**Backend endpoints:**
- `GET /payments/vendor/me/bank-accounts`
- `POST /payments/vendor/me/bank-accounts`
- `PUT /payments/vendor/me/bank-accounts/{id}`

**Files to create:**
- `app/components/vendor/settings/BankAccountsPanel.tsx` — Bank account CRUD

**Files to change:**
- `app/vendor/settings/page.tsx` → Add Bank Accounts tab

### 6.10 — Vendor Payout History

**Backend endpoints:**
- `GET /payments/vendor/me`

**Files to create:**
- `app/vendor/payouts/page.tsx` — Payout history page
- `app/components/vendor/payouts/PayoutList.tsx` — Payout listing with filters

### 6.11 — Cart "Move to Cart" from Wishlist

**Backend endpoint:**
- `POST /wishlist/me/items/{itemId}/move-to-cart`

**Files to change:**
- `app/wishlist/page.tsx` → Add "Move to Cart" button on each wishlist item
- `lib/types/wishlist.ts` → Ensure moveToCart action is typed

### 6.12 — Popular Searches

**Backend endpoint:**
- `GET /search/popular`

**Files to change:**
- `app/components/search/ProductSearchBar.tsx` → Show popular searches when search is empty/focused

### 6.13 — Auth Endpoints (Logout, Resend Verification)

**Backend endpoints:**
- `POST /auth/logout`
- `POST /auth/resend-verification`

**Files to verify/change:**
- `lib/authSession.ts` → Verify logout calls `POST /auth/logout` before Keycloak logout
- `app/profile/page.tsx` → Add "Resend Verification Email" button if email not verified

---

## Implementation Order

### Phase 1 — Foundation (Do First)
1. 0.1 — Centralize types
2. 0.2 — Fix PagedResponse
3. 0.3 — Zustand stores (auth, cart, wishlist, ui)
4. 0.4 — Constants file
5. 0.5 — Expand format.ts
6. 0.6 — Expand error.ts

### Phase 2 — Component Architecture
7. 1.7 — Shared ProductCard
8. 1.1 — Break home page
9. 1.2 — Break products page
10. 1.3 — Break product detail page
11. 1.4 — Break cart page
12. 1.14 — Break AppNav
13. 1.11 — Break profile page
14. 1.12 — Break orders page

### Phase 3 — API Alignment
15. 2.1-2.4 — Fix cart/wishlist/order/search types
16. 2.5-2.7 — Add save-for-later, invoices, cancellation
17. 2.8-2.10 — Add customer preferences, activity log, linked accounts
18. 2.11-2.16 — Add tracking, trending, reviews enhancements
19. 2.17-2.18 — Fix apiClient issues

### Phase 4 — UI/UX & Quality
20. 3.1-3.2 — Theme consistency, icon library
21. 3.3-3.4 — Loading states, error boundaries
22. 3.5-3.7 — Accessibility, button styles, empty states
23. 5.1-5.10 — Anti-patterns, race conditions, hooks

### Phase 5 — Performance & SSR
24. 4.1-4.2 — SSR migration, SEO metadata
25. 4.3 — Image optimization
26. 4.4-4.6 — Code splitting, event batching, table optimization

### Phase 6 — New Features
27. 6.1 — Wishlist collections
28. 6.2 — Admin payments
29. 6.3 — Admin reviews
30. 6.4-6.6 — Admin API keys, sessions, system config
31. 6.7-6.10 — Vendor reviews, bank accounts, payouts
32. 6.11-6.13 — Cart/wishlist integration, popular searches, auth endpoints

---

## Summary Statistics

| Category | Items | Estimated Files Changed | New Files |
|----------|-------|------------------------|-----------|
| P0 Foundation | 6 | 40+ | 20+ |
| P1 Component Architecture | 14 | 30+ | 50+ |
| P2 API Alignment | 18 | 25+ | 5+ |
| P3 UI/UX & Theming | 7 | 30+ | 10+ |
| P4 Performance & SSR | 6 | 15+ | 5+ |
| P5 Anti-Patterns | 10 | 30+ | 8+ |
| P6 New Features | 13 | 10+ | 30+ |
| **Total** | **74** | **~180 changes** | **~128 new files** |
