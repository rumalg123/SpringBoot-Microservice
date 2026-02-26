"use client";

import Pagination from "../Pagination";
import type { PublicPromotion, SpendTier } from "../../../lib/types/promotion";
import { formatDate } from "../../../lib/format";

/* ───── helpers ───── */

function formatBenefit(p: PublicPromotion): string {
  switch (p.benefitType) {
    case "PERCENTAGE_OFF":
      return `${p.benefitValue ?? 0}% OFF`;
    case "FIXED_AMOUNT_OFF":
      return `$${p.benefitValue ?? 0} OFF`;
    case "FREE_SHIPPING":
      return "FREE SHIPPING";
    case "BUY_X_GET_Y":
      return `Buy ${p.buyQuantity ?? 0} Get ${p.getQuantity ?? 0}`;
    case "TIERED_SPEND":
      return "Spend & Save";
    default:
      return p.benefitType.replace(/_/g, " ");
  }
}

function formatScopeLabel(scope: string): string {
  switch (scope) {
    case "PLATFORM": return "Platform";
    case "VENDOR": return "Vendor";
    case "CATEGORY": return "Category";
    default: return scope;
  }
}

function benefitColor(benefitType: string): { bg: string; border: string; text: string } {
  switch (benefitType) {
    case "PERCENTAGE_OFF":
      return { bg: "var(--brand-soft)", border: "var(--brand-glow)", text: "var(--brand)" };
    case "FIXED_AMOUNT_OFF":
      return { bg: "var(--success-soft)", border: "var(--success-glow)", text: "var(--success)" };
    case "FREE_SHIPPING":
      return { bg: "var(--accent-soft)", border: "var(--accent-glow)", text: "var(--accent)" };
    case "BUY_X_GET_Y":
      return { bg: "var(--warning-soft)", border: "var(--warning-border)", text: "var(--warning)" };
    case "TIERED_SPEND":
      return { bg: "var(--accent-soft)", border: "var(--accent-glow)", text: "var(--accent)" };
    default:
      return { bg: "rgba(255,255,255,0.05)", border: "var(--line-bright)", text: "var(--muted)" };
  }
}

/* ───── props ───── */

type PromotionDetailPanelProps = {
  promotions: PublicPromotion[];
  loading: boolean;
  totalPages: number;
  totalElements: number;
  page: number;
  search: string;
  scopeFilter: string;
  benefitFilter: string;
  debouncedSearch: string;
  onSearchChange: (value: string) => void;
  onScopeFilterChange: (value: string) => void;
  onBenefitFilterChange: (value: string) => void;
  onPageChange: (page: number) => void;
};

const inputCls = "flex-1 min-w-[200px] rounded-md border border-line-bright bg-[rgba(255,255,255,0.03)] py-2.5 pl-10 pr-3.5 text-sm font-medium text-white outline-none focus:border-brand";

export default function PromotionDetailPanel({
  promotions,
  loading,
  totalPages,
  totalElements,
  page,
  search,
  scopeFilter,
  benefitFilter,
  debouncedSearch,
  onSearchChange,
  onScopeFilterChange,
  onBenefitFilterChange,
  onPageChange,
}: PromotionDetailPanelProps) {
  return (
    <section>
      <h2 className="mb-4 mt-0 font-[Syne,sans-serif] text-[1.2rem] font-extrabold text-white">
        All Promotions
      </h2>

      {/* Search & Filters */}
      <div className="mb-6 flex flex-wrap items-center gap-3">
        {/* Search input with icon */}
        <div className="relative min-w-[200px] flex-1">
          <svg
            width="16"
            height="16"
            viewBox="0 0 24 24"
            fill="none"
            stroke="var(--muted)"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
            className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2"
          >
            <circle cx="11" cy="11" r="8" />
            <line x1="21" y1="21" x2="16.65" y2="16.65" />
          </svg>
          <input
            type="text"
            placeholder="Search promotions..."
            value={search}
            onChange={(e) => onSearchChange(e.target.value)}
            className={inputCls}
          />
        </div>

        {/* Scope filter */}
        <select
          value={scopeFilter}
          onChange={(e) => onScopeFilterChange(e.target.value)}
          className="filter-select"
        >
          <option value="">All Scopes</option>
          <option value="ORDER">Order</option>
          <option value="VENDOR">Vendor</option>
          <option value="PRODUCT">Product</option>
          <option value="CATEGORY">Category</option>
        </select>

        {/* Benefit filter */}
        <select
          value={benefitFilter}
          onChange={(e) => onBenefitFilterChange(e.target.value)}
          className="filter-select"
        >
          <option value="">All Benefits</option>
          <option value="PERCENTAGE_OFF">Percentage Off</option>
          <option value="FIXED_AMOUNT_OFF">Fixed Amount Off</option>
          <option value="FREE_SHIPPING">Free Shipping</option>
          <option value="BUY_X_GET_Y">Buy X Get Y</option>
          <option value="TIERED_SPEND">Tiered Spend</option>
        </select>
      </div>

      {/* Loading */}
      {loading && (
        <div className="py-[60px] text-center">
          <div className="spinner-lg" />
          <p className="mt-3 text-sm text-muted">Loading promotions...</p>
        </div>
      )}

      {/* Empty state */}
      {!loading && promotions.length === 0 && (
        <div className="empty-state">
          <div className="empty-state-icon">
            <svg width="44" height="44" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z" />
              <line x1="7" y1="7" x2="7.01" y2="7" />
            </svg>
          </div>
          <p className="empty-state-title">No promotions found</p>
          <p className="empty-state-desc">
            {debouncedSearch || scopeFilter || benefitFilter
              ? "Try adjusting your search or filters."
              : "Check back later for new deals and offers."}
          </p>
        </div>
      )}

      {/* Promotion Cards Grid */}
      {!loading && promotions.length > 0 && (
        <div className="grid gap-[18px]" style={{ gridTemplateColumns: "repeat(auto-fill, minmax(320px, 1fr))" }}>
          {promotions.map((promo) => {
            const bc = benefitColor(promo.benefitType);
            const hasDateRange = promo.startsAt || promo.endsAt;

            return (
              <article
                key={promo.id}
                className="animate-rise flex flex-col gap-3 rounded-lg border border-line bg-[rgba(255,255,255,0.03)] p-5 transition-all duration-200 hover:border-line-bright hover:shadow-[0_4px_24px_rgba(0,0,0,0.3)]"
              >
                {/* Top row: name + flash badge if applicable */}
                <div>
                  <h3 className="m-0 font-[Syne,sans-serif] text-lg font-extrabold leading-[1.35] text-white">
                    {promo.name}
                  </h3>
                  {promo.description && (
                    <p className="mt-1.5 line-clamp-2 text-sm leading-[1.5] text-muted">
                      {promo.description}
                    </p>
                  )}
                </div>

                {/* Badges row */}
                <div className="flex flex-wrap items-center gap-1.5">
                  {/* Benefit badge */}
                  <span
                    style={{
                      display: "inline-block",
                      padding: "4px 12px",
                      borderRadius: "20px",
                      background: bc.bg,
                      border: `1px solid ${bc.border}`,
                      color: bc.text,
                      fontSize: "0.72rem",
                      fontWeight: 800,
                      letterSpacing: "0.02em",
                    }}
                  >
                    {formatBenefit(promo)}
                  </span>

                  {/* Scope pill */}
                  <span
                    style={{
                      display: "inline-block",
                      padding: "3px 10px",
                      borderRadius: "20px",
                      background: "var(--brand-soft)",
                      border: "1px solid var(--line-bright)",
                      color: "var(--brand)",
                      fontSize: "0.65rem",
                      fontWeight: 700,
                      letterSpacing: "0.06em",
                      textTransform: "uppercase",
                    }}
                  >
                    {formatScopeLabel(promo.scopeType)}
                  </span>

                  {/* Flash sale indicator */}
                  {promo.flashSale && (
                    <span
                      style={{
                        display: "inline-flex",
                        alignItems: "center",
                        gap: "4px",
                        padding: "3px 10px",
                        borderRadius: "20px",
                        background: "rgba(251,191,36,0.12)",
                        border: "1px solid rgba(251,191,36,0.25)",
                        color: "#fbbf24",
                        fontSize: "0.65rem",
                        fontWeight: 700,
                        letterSpacing: "0.06em",
                      }}
                    >
                      <svg width="10" height="10" viewBox="0 0 24 24" fill="currentColor"><path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z" /></svg>
                      FLASH
                    </span>
                  )}

                  {/* Auto-apply badge */}
                  {promo.autoApply && (
                    <span
                      style={{
                        display: "inline-flex",
                        alignItems: "center",
                        gap: "4px",
                        padding: "3px 10px",
                        borderRadius: "20px",
                        background: "rgba(52,211,153,0.1)",
                        border: "1px solid rgba(52,211,153,0.25)",
                        color: "#34d399",
                        fontSize: "0.65rem",
                        fontWeight: 700,
                      }}
                    >
                      <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><polyline points="20 6 9 17 4 12" /></svg>
                      Auto-applies
                    </span>
                  )}

                  {/* Stackable badge */}
                  {promo.stackable && (
                    <span
                      style={{
                        display: "inline-flex",
                        alignItems: "center",
                        gap: "4px",
                        padding: "3px 10px",
                        borderRadius: "20px",
                        background: "rgba(167,139,250,0.1)",
                        border: "1px solid rgba(167,139,250,0.25)",
                        color: "#a78bfa",
                        fontSize: "0.65rem",
                        fontWeight: 700,
                      }}
                    >
                      <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><rect x="2" y="7" width="20" height="14" rx="2" ry="2" /><path d="M16 2v4" /><path d="M8 2v4" /></svg>
                      Stackable
                    </span>
                  )}
                </div>

                {/* Details row */}
                <div className="flex flex-wrap gap-3 text-xs text-muted">
                  {promo.minimumOrderAmount != null && promo.minimumOrderAmount > 0 && (
                    <span>Min. order: <strong className="text-ink-light">${promo.minimumOrderAmount}</strong></span>
                  )}
                  {promo.maximumDiscountAmount != null && promo.maximumDiscountAmount > 0 && (
                    <span>Max discount: <strong className="text-ink-light">${promo.maximumDiscountAmount}</strong></span>
                  )}
                  {promo.benefitType === "TIERED_SPEND" && promo.spendTiers.length > 0 && (
                    <span>
                      {promo.spendTiers.length} tier{promo.spendTiers.length !== 1 ? "s" : ""}
                    </span>
                  )}
                </div>

                {/* Spend tiers detail */}
                {promo.benefitType === "TIERED_SPEND" && promo.spendTiers.length > 0 && (
                  <div className="rounded-md border border-[rgba(244,114,182,0.15)] bg-[rgba(244,114,182,0.05)] px-3.5 py-2.5">
                    <p className="mb-1.5 mt-0 text-xs font-bold uppercase tracking-[0.06em] text-[#f472b6]">
                      Spend Tiers
                    </p>
                    {promo.spendTiers
                      .slice()
                      .sort((a, b) => a.thresholdAmount - b.thresholdAmount)
                      .map((tier, idx) => (
                        <div key={idx} className="py-0.5 text-xs text-muted">
                          Spend <strong className="text-ink-light">${tier.thresholdAmount}</strong> &rarr; Save <strong className="text-[#f472b6]">${tier.discountAmount}</strong>
                        </div>
                      ))}
                  </div>
                )}

                {/* Dates */}
                {hasDateRange && (
                  <div className="flex items-center gap-1.5 text-xs text-muted">
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <rect x="3" y="4" width="18" height="18" rx="2" ry="2" /><line x1="16" y1="2" x2="16" y2="6" /><line x1="8" y1="2" x2="8" y2="6" /><line x1="3" y1="10" x2="21" y2="10" />
                    </svg>
                    <span>
                      {promo.startsAt ? formatDate(promo.startsAt) : "Open"}{" "}&ndash;{" "}
                      {promo.endsAt ? formatDate(promo.endsAt) : "Ongoing"}
                    </span>
                  </div>
                )}
              </article>
            );
          })}
        </div>
      )}

      {/* Pagination */}
      {!loading && totalPages > 0 && (
        <Pagination
          currentPage={page}
          totalPages={totalPages}
          totalElements={totalElements}
          onPageChange={onPageChange}
          disabled={loading}
        />
      )}
    </section>
  );
}
