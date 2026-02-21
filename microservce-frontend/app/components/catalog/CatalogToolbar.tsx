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
  onClearSearch
}: Props) {
  return (
    <section className="mb-5 animate-rise rounded-xl bg-white p-5 shadow-sm">
      <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-[var(--ink)]">{title}</h1>
          <p className="mt-0.5 text-sm text-[var(--muted)]">{status}</p>
          <p className="mt-1 text-xs text-[var(--muted)]">{filterSummary}</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <label className="text-xs font-medium text-[var(--muted)]" htmlFor="product-sort">
            Sort by
          </label>
          <select
            id="product-sort"
            value={sortBy}
            onChange={(event) => onSortChange(event.target.value as SortKey)}
            disabled={loading}
            className="rounded-lg border border-[var(--line)] bg-white px-3 py-2 text-sm"
          >
            <option value="newest">Newest</option>
            <option value="priceAsc">Price: Low to High</option>
            <option value="priceDesc">Price: High to Low</option>
            <option value="nameAsc">Name: A to Z</option>
          </select>
          <button
            type="button"
            onClick={onResetFilters}
            disabled={loading}
            className="btn-outline px-3 py-2 text-xs disabled:cursor-not-allowed disabled:opacity-60"
          >
            Reset Filters
          </button>
        </div>
      </div>

      <form onSubmit={onSearchSubmit} className="grid gap-3 md:grid-cols-[1fr,auto]">
        <div className="relative flex items-center overflow-hidden rounded-lg border border-[var(--line)] bg-white">
          <span className="pl-3 text-[var(--muted)]">
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="11" cy="11" r="8" />
              <path d="m21 21-4.3-4.3" />
            </svg>
          </span>
          <input
            value={query}
            onChange={(event) => onQueryChange(event.target.value)}
            placeholder={searchPlaceholder}
            className="flex-1 border-none px-3 py-2.5 text-sm outline-none"
          />
          {query && (
            <button
              type="button"
              onClick={onClearSearch}
              disabled={loading}
              className="mr-2 flex h-6 w-6 items-center justify-center rounded-full bg-gray-200 text-xs text-gray-600 hover:bg-gray-300 disabled:cursor-not-allowed disabled:opacity-60"
              title="Clear search"
            >
              x
            </button>
          )}
        </div>
        <button
          type="submit"
          disabled={loading}
          className="btn-primary text-sm disabled:cursor-not-allowed disabled:opacity-60"
        >
          {loading ? "Searching..." : "Search"}
        </button>
      </form>
    </section>
  );
}
