"use client";

import { FormEvent } from "react";

type SortKey = "newest" | "priceAsc" | "priceDesc" | "nameAsc";

type Props = {
  title: string;
  status: string;
  filterSummary: string;
  sortBy: SortKey;
  loading: boolean;
  query: string;
  searchPlaceholder?: string;
  onSortChange: (value: SortKey) => void;
  onResetFilters: () => void;
  onQueryChange: (value: string) => void;
  onSearchSubmit: (event: FormEvent) => void;
  onClearSearch: () => void;
};

export default function CatalogToolbar({
  title,
  status,
  filterSummary,
  sortBy,
  loading,
  query,
  searchPlaceholder = "Search by name, description, SKU...",
  onSortChange,
  onResetFilters,
  onQueryChange,
  onSearchSubmit,
  onClearSearch,
}: Props) {
  return (
    <section
      className="mb-5 animate-rise bg-[rgba(17,17,40,0.7)] backdrop-blur-[16px] border border-brand/10 rounded-lg px-6 py-5"
    >
      {/* Title row */}
      <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="font-[Syne,sans-serif] text-[1.5rem] font-extrabold text-white m-0 tracking-tight">
            {title}
          </h1>
          <div className="mt-1 flex items-center gap-3">
            <p className="text-sm text-[#6868a0] m-0">{status}</p>
            {filterSummary !== "All Products" && (
              <span className="text-xs bg-brand-soft border border-brand/20 text-brand px-2.5 py-[2px] rounded-full font-semibold">
                {filterSummary}
              </span>
            )}
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <div className="relative">
            <select
              id="product-sort"
              value={sortBy}
              onChange={(e) => onSortChange(e.target.value as SortKey)}
              disabled={loading}
              className={`py-2 pl-3.5 pr-8 rounded-md border border-brand/15 bg-brand/[0.05] text-[#c8c8e8] text-sm font-semibold outline-none appearance-none ${loading ? "cursor-not-allowed" : "cursor-pointer"}`}
            >
              <option value="newest">Newest First</option>
              <option value="priceAsc">Price: Low → High</option>
              <option value="priceDesc">Price: High → Low</option>
              <option value="nameAsc">Name: A → Z</option>
            </select>
            <span className="absolute right-2.5 top-1/2 -translate-y-1/2 text-brand text-[0.55rem] pointer-events-none">
              ▼
            </span>
          </div>

          <button
            type="button"
            onClick={onResetFilters}
            disabled={loading}
            className={`py-2 px-4 rounded-md border border-danger-glow bg-danger-soft text-danger text-[0.78rem] font-bold transition-all duration-200 inline-flex items-center gap-1.5 hover:bg-[rgba(239,68,68,0.12)] ${loading ? "cursor-not-allowed opacity-50" : "cursor-pointer opacity-100"}`}
          >
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M3 6h18M8 6V4h8v2M19 6l-1 14H6L5 6" />
            </svg>
            Reset
          </button>
        </div>
      </div>

      {/* Search Row */}
      <form onSubmit={onSearchSubmit} className="grid gap-2.5 grid-cols-[1fr_auto]">
        <div className="flex items-center overflow-hidden rounded-md border border-brand/15 bg-brand/[0.03] transition-[border-color] duration-200 focus-within:border-brand/50 focus-within:shadow-[0_0_12px_rgba(0,212,255,0.15)]">
          <span className="pl-3.5 text-brand/40 shrink-0">
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none"
              stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="11" cy="11" r="8" /><path d="m21 21-4.3-4.3" />
            </svg>
          </span>
          <input
            value={query}
            onChange={(e) => onQueryChange(e.target.value)}
            placeholder={searchPlaceholder}
            className="flex-1 border-none bg-transparent px-3 py-[11px] text-base text-white outline-none"
          />
          {query && (
            <button
              type="button"
              onClick={onClearSearch}
              disabled={loading}
              className="mr-2.5 w-[22px] h-[22px] rounded-full border border-white/10 bg-white/[0.07] text-[#6868a0] text-[0.65rem] cursor-pointer flex items-center justify-center shrink-0 transition-all duration-150"
              title="Clear search"
            >
              ✕
            </button>
          )}
        </div>
        <button
          type="submit"
          disabled={loading}
          className={`px-[22px] py-[11px] rounded-md border-none text-white text-base font-bold transition-all duration-200 inline-flex items-center gap-2 whitespace-nowrap ${loading ? "bg-brand/30 cursor-not-allowed shadow-none" : "bg-[image:linear-gradient(135deg,#00d4ff,#7c3aed)] cursor-pointer shadow-[0_0_18px_rgba(0,212,255,0.2)]"}`}
        >
          {loading ? (
            <><span className="spinner-sm" />Searching...</>
          ) : (
            <>
              <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <circle cx="11" cy="11" r="8" /><path d="m21 21-4.3-4.3" />
              </svg>
              Search
            </>
          )}
        </button>
      </form>
    </section>
  );
}
