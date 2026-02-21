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
      className="mb-5 animate-rise"
      style={{
        background: "rgba(17,17,40,0.7)",
        backdropFilter: "blur(16px)",
        border: "1px solid rgba(0,212,255,0.1)",
        borderRadius: "16px",
        padding: "20px 24px",
      }}
    >
      {/* Title row */}
      <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1
            style={{
              fontFamily: "'Syne', sans-serif",
              fontSize: "1.5rem",
              fontWeight: 800,
              color: "#fff",
              margin: 0,
              letterSpacing: "-0.02em",
            }}
          >
            {title}
          </h1>
          <div className="mt-1 flex items-center gap-3">
            <p style={{ fontSize: "0.8rem", color: "#6868a0", margin: 0 }}>{status}</p>
            {filterSummary !== "All Products" && (
              <span
                style={{
                  fontSize: "0.7rem",
                  background: "rgba(0,212,255,0.08)",
                  border: "1px solid rgba(0,212,255,0.2)",
                  color: "#00d4ff",
                  padding: "2px 10px",
                  borderRadius: "20px",
                  fontWeight: 600,
                }}
              >
                {filterSummary}
              </span>
            )}
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <div style={{ position: "relative" }}>
            <select
              id="product-sort"
              value={sortBy}
              onChange={(e) => onSortChange(e.target.value as SortKey)}
              disabled={loading}
              style={{
                padding: "8px 32px 8px 14px",
                borderRadius: "10px",
                border: "1px solid rgba(0,212,255,0.15)",
                background: "rgba(0,212,255,0.05)",
                color: "#c8c8e8",
                fontSize: "0.8rem",
                fontWeight: 600,
                cursor: loading ? "not-allowed" : "pointer",
                outline: "none",
                appearance: "none",
                WebkitAppearance: "none",
              }}
            >
              <option value="newest">Newest First</option>
              <option value="priceAsc">Price: Low → High</option>
              <option value="priceDesc">Price: High → Low</option>
              <option value="nameAsc">Name: A → Z</option>
            </select>
            <span
              style={{
                position: "absolute",
                right: "10px",
                top: "50%",
                transform: "translateY(-50%)",
                color: "#00d4ff",
                fontSize: "0.55rem",
                pointerEvents: "none",
              }}
            >
              ▼
            </span>
          </div>

          <button
            type="button"
            onClick={onResetFilters}
            disabled={loading}
            style={{
              padding: "8px 16px",
              borderRadius: "10px",
              border: "1px solid rgba(239,68,68,0.25)",
              background: "rgba(239,68,68,0.05)",
              color: "#ef4444",
              fontSize: "0.78rem",
              fontWeight: 700,
              cursor: loading ? "not-allowed" : "pointer",
              opacity: loading ? 0.5 : 1,
              transition: "all 0.2s",
              display: "inline-flex",
              alignItems: "center",
              gap: "6px",
            }}
            onMouseEnter={(e) => { if (!loading) { e.currentTarget.style.background = "rgba(239,68,68,0.12)"; } }}
            onMouseLeave={(e) => { e.currentTarget.style.background = "rgba(239,68,68,0.05)"; }}
          >
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M3 6h18M8 6V4h8v2M19 6l-1 14H6L5 6" />
            </svg>
            Reset
          </button>
        </div>
      </div>

      {/* Search Row */}
      <form onSubmit={onSearchSubmit} style={{ display: "grid", gap: "10px", gridTemplateColumns: "1fr auto" }}>
        <div
          style={{
            display: "flex",
            alignItems: "center",
            overflow: "hidden",
            borderRadius: "10px",
            border: "1px solid rgba(0,212,255,0.15)",
            background: "rgba(0,212,255,0.03)",
            transition: "border-color 0.2s",
          }}
          onFocusCapture={(e) => { e.currentTarget.style.borderColor = "rgba(0,212,255,0.5)"; e.currentTarget.style.boxShadow = "0 0 12px rgba(0,212,255,0.15)"; }}
          onBlurCapture={(e) => { e.currentTarget.style.borderColor = "rgba(0,212,255,0.15)"; e.currentTarget.style.boxShadow = "none"; }}
        >
          <span style={{ paddingLeft: "14px", color: "rgba(0,212,255,0.4)", flexShrink: 0 }}>
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none"
              stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="11" cy="11" r="8" /><path d="m21 21-4.3-4.3" />
            </svg>
          </span>
          <input
            value={query}
            onChange={(e) => onQueryChange(e.target.value)}
            placeholder={searchPlaceholder}
            style={{
              flex: 1,
              border: "none",
              background: "transparent",
              padding: "11px 12px",
              fontSize: "0.875rem",
              color: "#fff",
              outline: "none",
            }}
          />
          {query && (
            <button
              type="button"
              onClick={onClearSearch}
              disabled={loading}
              style={{
                marginRight: "10px",
                width: "22px",
                height: "22px",
                borderRadius: "50%",
                border: "1px solid rgba(255,255,255,0.1)",
                background: "rgba(255,255,255,0.07)",
                color: "#6868a0",
                fontSize: "0.65rem",
                cursor: "pointer",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                flexShrink: 0,
                transition: "all 0.15s",
              }}
              title="Clear search"
            >
              ✕
            </button>
          )}
        </div>
        <button
          type="submit"
          disabled={loading}
          style={{
            padding: "11px 22px",
            borderRadius: "10px",
            border: "none",
            background: loading ? "rgba(0,212,255,0.3)" : "linear-gradient(135deg, #00d4ff, #7c3aed)",
            color: "#fff",
            fontSize: "0.875rem",
            fontWeight: 700,
            cursor: loading ? "not-allowed" : "pointer",
            transition: "all 0.2s",
            boxShadow: loading ? "none" : "0 0 18px rgba(0,212,255,0.2)",
            display: "inline-flex",
            alignItems: "center",
            gap: "8px",
            whiteSpace: "nowrap",
          }}
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
