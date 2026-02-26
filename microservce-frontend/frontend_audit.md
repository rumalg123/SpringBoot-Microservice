# Frontend Audit — Comprehensive Review

> **Codebase**: `microservce-frontend` (Next.js 16 / React 19 / Tailwind v4)
> **Date**: 2026-02-26
> **Scope**: Architecture, component quality, API contracts, styling, performance, accessibility

---

## Legend

| Tag | Meaning |
|-----|---------|
| **[TYPE-MISMATCH]** | Frontend type doesn't match backend DTO |
| **[MONOLITH]** | Page/component too large, needs breakdown |
| **[DUPLICATE]** | Same logic repeated across files |
| **[STYLE]** | Inline style or hardcoded value → migrate to Tailwind |
| **[ANTI-PATTERN]** | React/Next.js anti-pattern |
| **[DATA-FETCH]** | Data fetching improvement (React Query migration) |
| **[A11Y]** | Accessibility gap |
| **[PERF]** | Performance issue |
| **[MISSING]** | Missing feature or integration |

---

## 1. Type Mismatches (Backend DTO vs Frontend Types)

### 1.1 `ProductSummary` — Missing 15+ Fields [TYPE-MISMATCH]
- **File**: `lib/types/product.ts`
- **Issue**: Frontend `ProductSummary` is missing fields the backend `ProductSummaryResponse` returns.
- **Missing fields to add**: `brandName`, `mainCategory`, `subCategories`, `approvalStatus`, `vendorId`, `viewCount`, `soldCount`, `active`, `variations` (array of `{name,value}`), `createdAt`, `updatedAt`, `stockAvailable`, `stockStatus`, `backorderable`
- **Impact**: Admin product tables can't display stock/vendor info; catalog can't show brand or stock status.

### 1.2 `ProductDetail` — Missing 20+ Fields [TYPE-MISMATCH]
- **File**: `lib/types/product.ts`
- **Issue**: Frontend `ProductDetail` is missing significant fields the backend `ProductResponse` returns.
- **Missing fields to add**: `brandName`, `thumbnailUrl`, `categoryIds`, `digital`, `weightGrams`, `lengthCm`, `widthCm`, `heightCm`, `metaTitle`, `metaDescription`, `approvalStatus`, `rejectionReason`, `specifications` (array), `bundledProductIds`, `viewCount`, `soldCount`, `active`, `deleted`, `deletedAt`, `createdAt`, `updatedAt`, `stockAvailable`, `stockStatus`, `backorderable`
- **Impact**: Product detail page can't show specifications, shipping dimensions, brand, or stock availability.

### 1.3 All Other Types — Verified Match
- `CartResponse`, `CartItem`, `CheckoutPreviewResponse`, `CheckoutResponse` → **Match**
- `Order`, `OrderDetail`, `OrderItem`, `VendorOrder` → **Match**
- `Customer`, `CustomerAddress`, `CommunicationPreferences` → **Match**
- `WishlistItem`, `WishlistResponse`, `WishlistCollection` → **Match**
- `SearchResponse`, `SearchHit`, `FacetGroup`, `AutocompleteResponse` → **Match**
- `Review`, `ReviewSummary` → **Match**
- `PublicPromotion`, `SpendTier` → **Match**
- `PersonalizationProduct`, `TrackEventPayload` → **Match**
- `CustomerInsights`, `CustomerOrderSummary` → **Match**

---

## 2. Monolithic Pages Requiring Breakdown

### 2.1 `app/profile/page.tsx` — 1,239 Lines, 24 useState [MONOLITH]
- **Extract `AccountInfoCard`** (lines ~477-599): Profile display, name edit form, session info card
- **Extract `AddressBook`** (lines ~600-800): Address list grid, add/edit form, default address toggles, delete
- **Extract `CommunicationPrefsTab`** (lines ~800-900): Toggle switches with loading states for email/SMS/push preferences
- **Extract `LinkedAccountsTab`**: OAuth linked accounts display
- **Extract `ActivityLogTab`** (lines ~970-1100): Paginated activity log with table rows
- **Extract `CouponUsageTab`** (lines ~1128-1239): Paginated coupon history

### 2.2 `app/wishlist/page.tsx` — 992 Lines, 25 useState [MONOLITH]
- **Extract `CollectionTabs`** (lines ~363-516): Tab navigation bar with "new collection" input
- **Extract `CollectionHeader`** (lines ~518-658): Editable collection name/description, share toggle, delete
- **Extract `WishlistItemCard`** (lines ~680-800+): Product card with note editor, move-to-cart, remove
- **Extract `WishlistGrid`**: Grid wrapper for items with empty state

### 2.3 `app/orders/page.tsx` — 741 Lines [MONOLITH]
- **Extract `OrderDetailPanel`** (lines ~560-730): 170-line IIFE rendering order details — replace with component
- **Extract `OrderHistoryCard`** (lines ~295-415): Order summary card with status, actions, cancel form
- **Extract `QuickPurchaseForm`** (lines ~424-520): Product select, address select, place order form

### 2.4 `app/admin/promotions/page.tsx` — 1,182 Lines, 31 useState [MONOLITH]
- **Extract `PromotionForm`** (lines ~755-949): Complex form with conditional benefit type fields
- **Extract `PromotionDetailPanel`** (lines ~568-752): 3-tab detail view (details, coupons, analytics)
- **Extract `CouponsTab`** (lines ~677-720): Coupon code creation and list
- **Extract `PromotionsList`** (lines ~951-1025): Filtered, paginated list of promotions
- **Extract `PromotionAnalyticsTable`** (lines ~1029-1176): Analytics table with filters and pagination
- **Extract promotion types** to `app/components/admin/promotions/types.ts`

### 2.5 `app/admin/posters/page.tsx` — 1,114 Lines, 23 useState [MONOLITH]
- **Extract `PosterForm`** (lines ~787-909): Form with image uploads, link target, scheduling
- **Extract `VariantsSection`** (lines ~911-1011): A/B variant management with CRUD
- **Extract `PosterAnalyticsPanel`** (lines ~732-785): Analytics summary and per-poster table
- **Extract `PosterList`** (lines ~1014-1068): Grouped poster cards with actions
- **Extract poster types** to `app/components/admin/posters/types.ts`

### 2.6 `app/admin/dashboard/page.tsx` — 770 Lines [MONOLITH]
- **Extract `OverviewTab`**: Revenue chart, order breakdown, payment summary
- **Extract `ProductsTab`**: Top products table
- **Extract `CustomersTab`**: Loyalty pie chart, growth trend
- **Extract `VendorsTab`**: Vendor leaderboard
- **Extract `InventoryTab`**: Stock status chart, low stock table
- **Extract `PromotionsTab`**: ROI and utilization
- **Extract `ReviewsTab`**: Rating distribution chart

### 2.7 `app/admin/settings/page.tsx` — 603 Lines [MONOLITH]
- **Extract `SystemConfigTab`** (lines ~271-422): Config CRUD
- **Extract `FeatureFlagsTab`** (lines ~426-596): Feature flag CRUD
- Both tabs share identical CRUD patterns → create a `useCRUD` hook

### 2.8 `app/vendor/settings/page.tsx` — 817 Lines [MONOLITH]
- **Extract `VendorProfileForm`** (lines ~448-577): Profile editing
- **Extract `PayoutConfigForm`** (lines ~579-654): Payout settings
- **Extract `VendorActionsPanel`** (lines ~659-769): Verification, order toggle

### 2.9 `app/vendor/orders/page.tsx` — 712 Lines [MONOLITH]
- **Extract `VendorOrderDetailPanel`** (lines ~482-657): Order detail with items table and shipping address
- **Extract `VendorOrdersTable`**: Order list with filters and pagination

### 2.10 `app/cart/page.tsx` — 555 Lines [MONOLITH]
- **Extract `CartItemCard`** (lines ~346-431): Item display with quantity stepper and actions
- **Extract `SavedForLaterSection`** (lines ~463-524): Saved items grid
- **Extract `CartSuggestions`** (lines ~528-550): "Complete your purchase" horizontal scroll

---

## 3. Duplicated Cross-Page Patterns → Extract Reusable Components

### 3.1 `EmailVerificationWarning` [DUPLICATE]
- **Appears in**: `cart/page.tsx`, `orders/page.tsx`, `profile/page.tsx`
- **Pattern**: Identical warning banner with resend button and loading state
- **Action**: Create `app/components/ui/EmailVerificationWarning.tsx`
  - Props: `emailVerified: boolean | null`, `onResend: () => Promise<void>`, `resending: boolean`

### 3.2 `FullScreenLoader` [DUPLICATE]
- **Appears in**: All 5 customer pages + most admin/vendor pages
- **Pattern**: `minHeight: "100vh"`, `background: "var(--bg)"`, `display: "grid"`, `placeItems: "center"`, spinner
- **Action**: Create `app/components/ui/FullScreenLoader.tsx`

### 3.3 `PageHeader` [DUPLICATE]
- **Appears in**: `cart`, `orders`, `profile`, `wishlist`, `categories`, all vendor pages
- **Pattern**: Title with gradient, subtitle, optional action buttons, breadcrumb
- **Action**: Create `app/components/ui/PageHeader.tsx`
  - Props: `title: string`, `subtitle?: string`, `actions?: ReactNode`, `breadcrumbs?: Crumb[]`

### 3.4 AppNav Prop Drilling [DUPLICATE]
- **Appears in**: Every page passes 11+ identical props from `useAuthSession()` to `<AppNav>`
- **Action**: Wrap AppNav in a `ConnectedAppNav` that calls `useAuthSession()` internally, or use a shared layout
- **Files to change**: Every page.tsx that renders `<AppNav ... />`

### 3.5 Inline Pagination [DUPLICATE]
- **Appears in**: `admin/payments`, `admin/sessions`, `admin/reviews`, `admin/promotions` (2x), `admin/posters`, `vendor/orders`, `vendor/reviews`, `vendor/payouts`, `profile` (2x)
- **Pattern**: Prev/Next buttons with page indicator, disabled states
- **Action**: Already have `app/components/Pagination.tsx` — ensure all pages use it instead of inline implementations

### 3.6 Tab Navigation [DUPLICATE]
- **Appears in**: `admin/dashboard`, `admin/inventory`, `admin/payments`, `admin/settings`, `admin/reviews`, `vendor/settings`, `vendor/inventory`, `profile`
- **Pattern**: Row of tab buttons with active state styling, conditional tab content rendering
- **Action**: Create `app/components/ui/Tabs.tsx`
  - Props: `tabs: { key: string, label: string }[]`, `activeKey: string`, `onChange: (key: string) => void`

### 3.7 Inline StatusBadge Functions [DUPLICATE]
- **Appears in**: `admin/payments`, `admin/api-keys`, `vendor/orders`, `vendor/payouts`
- **Pattern**: Custom inline `statusBadge()` function instead of using the existing `StatusBadge` component
- **Action**: Replace all inline badge functions with `<StatusBadge>` from `app/components/ui/StatusBadge.tsx`

### 3.8 `NavDropdownWidget` Base [DUPLICATE]
- **Files**: `CartNavWidget.tsx`, `WishlistNavWidget.tsx`
- **Issue**: 90% identical code (popup positioning, badge rendering, open/close logic, hover styles)
- **Action**: Extract shared `NavDropdownWidget` base component
  - Props: `icon`, `count`, `badgeGradient`, `children`, `onOpen`

### 3.9 Admin Page Shell Inconsistency [DUPLICATE]
- **Files using `AdminPageShell`**: `dashboard`, `inventory`, `access-audit`, `permission-groups`
- **Files building own shell**: `payments`, `sessions`, `settings`, `api-keys`, `promotions`, `reviews`, `posters`, `platform-staff`, `vendor-staff`
- **Action**: Migrate all admin pages to use `AdminPageShell` consistently

### 3.10 Vendor Page Shell [MISSING]
- **Issue**: No `VendorPageShell` equivalent — every vendor page manually renders AppNav + auth guards + Footer
- **Action**: Create `app/components/ui/VendorPageShell.tsx` mirroring `AdminPageShell`

---

## 4. Styling: Migrate from Inline Styles to Tailwind

### 4.1 Global Strategy
- **Current**: ~95% inline `React.CSSProperties`, ~5% CSS classes from `globals.css`
- **Target**: Tailwind utility classes using CSS variables defined in `globals.css`
- **Tailwind v4 config**: Already installed via `@tailwindcss/postcss` — define custom theme tokens mapping to existing CSS variables

### 4.2 Tailwind Theme Setup
- **File**: `app/globals.css`
- **Action**: Add Tailwind `@theme` block mapping CSS variables to Tailwind tokens:
  - Colors: `--color-bg`, `--color-surface`, `--color-ink`, `--color-brand`, etc.
  - Spacing: standardize padding/margin values
  - Border radius: `--radius-sm` (6px), `--radius-md` (10px), `--radius-lg` (16px)
  - Font sizes: consolidate 10+ variants → 5 standard sizes (`text-xs` through `text-lg`)

### 4.3 Hardcoded Colors to Replace (High-Frequency)
| Hardcoded Value | Occurrences | Tailwind Token |
|---|---|---|
| `#fff` | 50+ | `text-white` |
| `#a78bfa` | 10+ | `text-accent-light` (new token) |
| `rgba(0,212,255,0.08)` | 20+ | `bg-brand/8` (with opacity) |
| `rgba(0,212,255,0.25)` | 15+ | `border-brand/25` |
| `rgba(124,58,237,0.12)` | 15+ | `bg-accent/12` |
| `rgba(124,58,237,0.25)` | 10+ | `border-accent/25` |
| `rgba(245,158,11,0.2)` | 5+ | `bg-warning/20` |
| `rgba(239,68,68,0.06)` | 5+ | `bg-danger/6` |
| `#111128` | 5+ | `bg-surface` |
| `#6868a0` | 5+ | `text-muted` |
| `#4a4a70` | 5+ | `text-muted-2` |

### 4.4 onMouseEnter/Leave → CSS hover [STYLE][PERF]
- **Appears in**: `Footer.tsx`, `CartNavWidget.tsx`, `WishlistNavWidget.tsx`, `ProductCard.tsx`, `DataTable.tsx`, `profile/page.tsx`, `orders/page.tsx`, many admin pages
- **Pattern**: `onMouseEnter={e => e.currentTarget.style.X = Y}` / `onMouseLeave`
- **Action**: Replace with Tailwind `hover:` variants (e.g., `hover:border-brand hover:bg-brand/8`)

### 4.5 Font Size Consolidation [STYLE]
- **Current 10+ sizes**: `0.65rem`, `0.7rem`, `0.72rem`, `0.75rem`, `0.78rem`, `0.8rem`, `0.82rem`, `0.85rem`, `0.875rem`, `0.9rem`
- **Consolidate to 5**:
  - `text-xs` = `0.7rem` (tiny labels, badges)
  - `text-sm` = `0.8rem` (secondary text, metadata)
  - `text-base` = `0.875rem` (body text)
  - `text-lg` = `1rem` (headings)
  - `text-xl` = `1.25rem` (page titles)

---

## 5. React / Next.js Anti-Patterns

### 5.1 Silent Error Swallowing [ANTI-PATTERN]
- **Files**: `app/page.tsx` (line 57-59), `cart/page.tsx` (line 93), `orders/page.tsx` (line 118), many admin pages
- **Pattern**: `.catch(() => {})` or `.catch(() => null)` silently suppresses errors
- **Fix**: Log errors + set error state, or at minimum show toast notification

### 5.2 Excessive useState — Use useReducer [ANTI-PATTERN]
- **Files and counts**:
  - `admin/promotions/page.tsx`: 31 useState
  - `app/wishlist/page.tsx`: 25 useState
  - `app/profile/page.tsx`: 24 useState
  - `admin/posters/page.tsx`: 23 useState
  - `admin/payments/page.tsx`: 19 useState
  - `app/cart/page.tsx`: 16 useState
- **Fix**: Group related state into useReducer or combine into state objects. With React Query migration, most loading/error states go away.

### 5.3 IIFE in JSX Render [ANTI-PATTERN]
- **File**: `orders/page.tsx` (line ~560)
- **Pattern**: `{selectedDetail && (() => { ... 170 lines of JSX ... })()}`
- **Fix**: Extract to `<OrderDetailPanel>` component

### 5.4 eslint-disable-line for Dependency Arrays [ANTI-PATTERN]
- **Files**: `admin/promotions/page.tsx` (lines 307-312), `admin/posters/page.tsx` (lines 465-489)
- **Fix**: Properly declare dependencies or restructure with useCallback

### 5.5 Missing Memoization [PERF]
- **Files**: `profile/page.tsx` (tab definitions recreated every render), `admin/dashboard/page.tsx` (chart data transforms), `Footer.tsx` (link arrays)
- **Fix**: Wrap stable arrays/objects in `useMemo`, callbacks in `useCallback`

### 5.6 No `React.memo` on Pure Display Components [PERF]
- **Components**: `ProductCard`, `StatusBadge`, `EmptyState`, `StarRating`, `ReviewCard`, `TrustBar`
- **Fix**: Wrap with `React.memo()` — these are pure presentation with no internal state

---

## 6. Data Fetching — React Query Migration [DATA-FETCH]

### 6.1 Install & Setup
- **Action**: Install `@tanstack/react-query`
- **Create**: `app/providers.tsx` with `QueryClientProvider`
- **Update**: `app/layout.tsx` to wrap children with `Providers`

### 6.2 Custom Query Hooks to Create
Each replaces manual useState + useEffect + loading/error management:

| Hook | Replaces Manual Fetch In | Key |
|---|---|---|
| `useCart()` | `cart/page.tsx` | `["cart"]` |
| `useAddresses()` | `cart/page.tsx`, `orders/page.tsx`, `profile/page.tsx` | `["addresses"]` |
| `useOrders()` | `orders/page.tsx` | `["orders"]` |
| `useOrderDetail(id)` | `orders/page.tsx` | `["order", id]` |
| `useCustomerProfile()` | `profile/page.tsx` | `["customer"]` |
| `useWishlist()` | `wishlist/page.tsx` | `["wishlist"]` |
| `useWishlistCollections()` | `wishlist/page.tsx` | `["wishlist-collections"]` |
| `useProducts(params)` | `app/page.tsx`, `products/page.tsx` | `["products", params]` |
| `useProductDetail(id)` | `products/[id]/ProductDetailClient.tsx` | `["product", id]` |
| `useCategories()` | `categories/page.tsx`, `CategoryMenu.tsx`, catalog pages | `["categories"]` |
| `useReviews(productId)` | `ProductDetailClient.tsx` | `["reviews", productId]` |
| `useTrending()` | `app/page.tsx` | `["trending"]` |
| `useRecommended()` | `app/page.tsx` | `["recommended"]` |
| `useCustomerInsights()` | `profile/insights/page.tsx` | `["insights"]` |

### 6.3 Mutation Hooks to Create
| Hook | Action | Invalidates |
|---|---|---|
| `useAddToCart()` | POST /cart/me/items | `["cart"]` |
| `useUpdateCartItem()` | PUT /cart/me/items/{id} | `["cart"]` |
| `useRemoveCartItem()` | DELETE /cart/me/items/{id} | `["cart"]` |
| `useCheckout()` | POST /cart/me/checkout | `["cart", "orders"]` |
| `usePlaceOrder()` | POST /orders/me | `["orders"]` |
| `useCancelOrder()` | POST /orders/me/{id}/cancel | `["orders", "order"]` |
| `useUpdateProfile()` | PUT /customers/me | `["customer"]` |
| `useToggleWishlist()` | POST/DELETE wishlist items | `["wishlist"]` |

---

## 7. Missing Features & Integrations

### 7.1 No Admin/Vendor Layouts [MISSING]
- **Issue**: No `app/admin/layout.tsx` or `app/vendor/layout.tsx` — every page manually renders AppNav + Footer + auth guards
- **Action**: Create nested layouts:
  - `app/admin/layout.tsx` with `AdminPageShell` wrapper, auth guard, sidebar nav
  - `app/vendor/layout.tsx` with `VendorPageShell` wrapper, auth guard, sidebar nav
- **Impact**: Eliminates ~30 lines of boilerplate per admin/vendor page

### 7.2 No Middleware for Auth Protection [MISSING]
- **Issue**: No `middleware.ts` — auth is purely client-side, meaning protected routes flash content before redirect
- **Action**: Add `middleware.ts` that checks for Keycloak session cookie and redirects unauthenticated users from `/admin/*`, `/vendor/*`, `/profile/*`, `/cart/*`, `/orders/*`, `/wishlist/*`

### 7.3 No `next/image` Usage [PERF][MISSING]
- **Issue**: All product images use `<img>` tags — no lazy loading, no size optimization, no WebP conversion
- **Files**: `ProductCard.tsx`, `ProductImageGallery.tsx`, `CartNavWidget.tsx`, `WishlistNavWidget.tsx`, `PosterSlot.tsx`, all admin/vendor product displays
- **Action**: Replace `<img>` with `<Image>` from `next/image` with proper `width`/`height` or `fill` props
- **Note**: Requires updating `next.config.ts` with allowed image domains

### 7.4 Missing Product Detail Data Display [MISSING]
- **Issue**: Backend returns `specifications`, `brandName`, `stockStatus`, `weightGrams`, etc. but frontend doesn't display them
- **File**: `app/products/[id]/ProductDetailClient.tsx`
- **Action**: Add specifications section, brand display, stock availability indicator, shipping dimensions

### 7.5 Missing Search Facet Filters [MISSING]
- **Issue**: Backend search returns `facets` (filter groups with counts) but frontend only uses category and price filters
- **File**: `app/products/page.tsx`, `CatalogFiltersSidebar.tsx`
- **Action**: Render dynamic facet filters from search response (brand, price range, etc.)

---

## 8. Accessibility Gaps [A11Y]

### 8.1 Dropdown/Popup ARIA Attributes
- **Files**: `CartNavWidget.tsx`, `WishlistNavWidget.tsx`, `CategoryMenu.tsx`, `ProductSearchBar.tsx`
- **Missing**: `role="menu"`, `role="listbox"`, `aria-hidden` on closed popups, focus trap, keyboard navigation (arrow keys)

### 8.2 Missing `aria-live` Regions
- **Cart badge**: Needs `aria-live="polite"` for screen readers when count changes
- **Wishlist badge**: Same
- **Search suggestions**: Needs `aria-live="polite"` for suggestion updates

### 8.3 Missing Semantic Landmarks
- **`AdminPageShell.tsx`**: Missing `<main>` wrapper with `role="main"`
- **`Footer.tsx`**: Footer nav sections need `<nav>` wrapper
- **Breadcrumbs**: Need `aria-label="Breadcrumbs"` and `aria-current="page"` on current item

### 8.4 Form Accessibility
- **`PurchasePanel.tsx`**: Quantity controls lack `aria-label`; select elements lack `aria-describedby`
- **`CatalogFiltersSidebar.tsx`**: Price inputs lack `aria-describedby` for context
- **`ReviewForm.tsx`**: Star rating needs proper `aria-label` per star

---

## 9. Performance Optimizations [PERF]

### 9.1 Scroll Event Throttling
- **File**: `AppNav.tsx` (line ~72-76)
- **Issue**: Scroll event listener fires on every scroll pixel without throttling
- **Fix**: Add `requestAnimationFrame` throttle or passive listener

### 9.2 Inline Style Object Recreation
- **Files**: All pages with inline `style={{...}}` — creates new objects every render
- **Fix**: Migrating to Tailwind resolves this entirely. For remaining inline styles, extract to module-level constants.

### 9.3 Component Code Splitting
- **Issue**: Admin pages (promotions: 1182 lines, posters: 1114 lines, dashboard: 770 lines) are massive client bundles
- **Fix**: After breaking into sub-components, use `React.lazy` + `Suspense` for tab content that's not immediately visible

---

## 10. Implementation Roadmap

### Phase 1: Foundation (Infrastructure)
1. Set up Tailwind v4 theme tokens in `globals.css`
2. Install & configure React Query (`@tanstack/react-query`)
3. Create `app/providers.tsx` with QueryClientProvider
4. Create admin layout (`app/admin/layout.tsx`)
5. Create vendor layout (`app/vendor/layout.tsx`)
6. Create `ConnectedAppNav` (self-contained auth session access)

### Phase 2: Shared Components
7. Create `EmailVerificationWarning` component
8. Create `FullScreenLoader` component
9. Create `PageHeader` component
10. Create `Tabs` component
11. Create `NavDropdownWidget` base for cart/wishlist widgets
12. Create `VendorPageShell` component
13. Ensure all pages use existing `Pagination`, `DataTable`, `FilterBar`, `StatusBadge`

### Phase 3: Type Fixes
14. Update `ProductSummary` type with missing backend fields
15. Update `ProductDetail` type with missing backend fields

### Phase 4: Page Breakdowns (Customer)
16. Break down `profile/page.tsx` → 6 sub-components
17. Break down `wishlist/page.tsx` → 4 sub-components
18. Break down `orders/page.tsx` → 3 sub-components
19. Break down `cart/page.tsx` → 3 sub-components

### Phase 5: Page Breakdowns (Admin)
20. Break down `admin/promotions/page.tsx` → 5 sub-components + types file
21. Break down `admin/posters/page.tsx` → 4 sub-components + types file
22. Break down `admin/dashboard/page.tsx` → 7 tab components
23. Break down `admin/settings/page.tsx` → 2 tab components + useCRUD hook
24. Migrate all admin pages to use `AdminPageShell`

### Phase 6: Page Breakdowns (Vendor)
25. Break down `vendor/settings/page.tsx` → 3 sub-components
26. Break down `vendor/orders/page.tsx` → 2 sub-components
27. Migrate all vendor pages to use `VendorPageShell`

### Phase 7: React Query Migration
28. Create query hooks (useCart, useAddresses, useOrders, etc.)
29. Create mutation hooks (useAddToCart, useCheckout, etc.)
30. Migrate customer pages to React Query
31. Migrate admin pages to React Query
32. Migrate vendor pages to React Query

### Phase 8: Tailwind Migration
33. Migrate shared components to Tailwind
34. Migrate customer pages to Tailwind
35. Migrate admin pages to Tailwind
36. Migrate vendor pages to Tailwind

### Phase 9: Polish
37. Replace `<img>` with `next/image` across all components
38. Add missing ARIA attributes and keyboard navigation
39. Add `React.memo` to pure display components
40. Fix all silent error swallowing patterns
41. Add scroll event throttling to AppNav

---

*End of audit*
