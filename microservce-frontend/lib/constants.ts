export const API_BASE =
  (process.env.NEXT_PUBLIC_API_BASE || "https://gateway.rumalg.me").replace(/\/+$/, "");

/* ── Pagination ── */
export const PAGE_SIZE_DEFAULT = 20;
export const PAGE_SIZE_SMALL = 12;
export const PAGE_SIZE_CARD = 8;
export const PAGE_SIZE_LARGE = 50;
export const AGGREGATE_PAGE_SIZE = 100;
export const AGGREGATE_MAX_PAGES = 10;

/* ── Shipping ── */
export const FREE_SHIPPING_THRESHOLD = 25;

/* ── Idempotency ── */
export const IDEMPOTENCY_WINDOW_MS = 15_000;
export const IDEMPOTENCY_MAX_CACHE_SIZE = 100;

/* ── Order status → theme colour tokens ── */
export const ORDER_STATUS_COLORS: Record<string, { bg: string; border: string; color: string }> = {
  PAYMENT_PENDING: { bg: "var(--warning-soft)", border: "var(--warning-border)", color: "var(--warning-text)" },
  PAYMENT_FAILED:  { bg: "var(--danger-soft)",  border: "var(--danger-glow)",   color: "var(--danger)" },
  PENDING:         { bg: "var(--warning-soft)", border: "var(--warning-border)", color: "var(--warning-text)" },
  CONFIRMED:       { bg: "var(--brand-soft)",   border: "var(--line-bright)",    color: "var(--brand)" },
  PROCESSING:      { bg: "var(--brand-soft)",   border: "var(--line-bright)",    color: "var(--brand)" },
  SHIPPED:         { bg: "var(--accent-soft)",  border: "var(--accent-glow)",    color: "var(--accent)" },
  DELIVERED:       { bg: "var(--success-soft)", border: "var(--success-glow)",   color: "var(--success)" },
  CANCELLED:       { bg: "var(--danger-soft)",  border: "var(--danger-glow)",    color: "var(--danger)" },
  REFUNDED:        { bg: "var(--danger-soft)",  border: "var(--danger-glow)",    color: "var(--danger)" },
  FAILED:          { bg: "var(--danger-soft)",  border: "var(--danger-glow)",    color: "var(--danger)" },
  ON_HOLD:         { bg: "var(--warning-soft)", border: "var(--warning-border)", color: "var(--warning-text)" },
  RETURNED:        { bg: "var(--danger-soft)",  border: "var(--danger-glow)",    color: "var(--danger)" },
  PARTIALLY_FULFILLED: { bg: "var(--brand-soft)", border: "var(--line-bright)",  color: "var(--brand)" },
};

/* ── Product sort options ── */
export const PRODUCT_SORT_OPTIONS = [
  { value: "newest",   label: "Newest" },
  { value: "priceAsc", label: "Price: Low to High" },
  { value: "priceDesc",label: "Price: High to Low" },
  { value: "nameAsc",  label: "Name: A–Z" },
] as const;

/* ── Loyalty tier colours ── */
export const TIER_COLORS: Record<string, string> = {
  BRONZE:   "#cd7f32",
  SILVER:   "#c0c0c0",
  GOLD:     "#fbbf24",
  PLATINUM: "#a78bfa",
  DIAMOND:  "#00d4ff",
};

/* ── Chart styling ── */
export const CHART_GRID = "rgba(120,120,200,0.08)";
export const CHART_TEXT = "#6868a0";
