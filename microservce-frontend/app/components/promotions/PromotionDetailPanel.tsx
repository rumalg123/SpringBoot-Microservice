"use client";

import Pagination from "../Pagination";
import type { PublicPromotion } from "../../../lib/types/promotion";
import PromotionCard from "./PromotionCard";

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
          {promotions.map((promo) => (
            <PromotionCard key={promo.id} promo={promo} />
          ))}
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
