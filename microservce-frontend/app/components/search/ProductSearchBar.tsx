"use client";

import { CSSProperties, useEffect, useRef, useState } from "react";
import { usePathname, useRouter } from "next/navigation";

type ProductSummary = {
  id: string;
  slug: string;
  name: string;
  sku: string;
};

type ProductPageResponse = {
  content?: ProductSummary[];
};

type ProductSearchBarProps = {
  className?: string;
  style?: CSSProperties;
  maxWidth?: number | string;
  placeholder?: string;
};

export default function ProductSearchBar({
  className,
  style,
  maxWidth = 520,
  placeholder = "Search products, brands and more...",
}: ProductSearchBarProps) {
  const router = useRouter();
  const pathname = usePathname();
  const [searchText, setSearchText] = useState("");
  const [searchSuggestions, setSearchSuggestions] = useState<ProductSummary[]>([]);
  const [searchSuggestionsLoading, setSearchSuggestionsLoading] = useState(false);
  const [searchDropdownOpen, setSearchDropdownOpen] = useState(false);
  const [searchSubmitPending, setSearchSubmitPending] = useState(false);
  const searchBoxRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    setSearchSubmitPending(false);
  }, [pathname]);

  useEffect(() => {
    const onPointerDown = (event: MouseEvent) => {
      if (!searchBoxRef.current) return;
      if (!searchBoxRef.current.contains(event.target as Node)) {
        setSearchDropdownOpen(false);
      }
    };
    document.addEventListener("mousedown", onPointerDown);
    return () => document.removeEventListener("mousedown", onPointerDown);
  }, []);

  useEffect(() => {
    const term = searchText.trim();
    if (term.length < 2) {
      setSearchSuggestions([]);
      setSearchSuggestionsLoading(false);
      return;
    }

    let active = true;
    const timer = window.setTimeout(async () => {
      setSearchSuggestionsLoading(true);
      try {
        const apiBase = (process.env.NEXT_PUBLIC_API_BASE || "https://gateway.rumalg.me").trim();
        const params = new URLSearchParams({ page: "0", size: "6", q: term });
        const res = await fetch(`${apiBase}/products?${params.toString()}`, { cache: "no-store" });
        if (!res.ok) throw new Error("Failed");
        const data = (await res.json()) as ProductPageResponse;
        if (!active) return;
        setSearchSuggestions((data.content || []).slice(0, 6));
      } catch {
        if (!active) return;
        setSearchSuggestions([]);
      } finally {
        if (active) setSearchSuggestionsLoading(false);
      }
    }, 250);

    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, [searchText]);

  const openSearchResults = (value: string) => {
    const normalized = value.trim();
    if (!normalized) return;
    setSearchSubmitPending(true);
    setSearchDropdownOpen(false);
    router.push(`/products?q=${encodeURIComponent(normalized)}`);
  };

  const openSuggestedProduct = (product: ProductSummary) => {
    setSearchDropdownOpen(false);
    router.push(`/products/${encodeURIComponent((product.slug || product.id).trim())}`);
  };

  return (
    <div ref={searchBoxRef} className={className} style={{ position: "relative", maxWidth, ...style }}>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          if (searchSubmitPending) return;
          openSearchResults(searchText);
        }}
        style={{
          display: "flex",
          alignItems: "center",
          overflow: "hidden",
          borderRadius: "12px",
          border: "1px solid rgba(0,212,255,0.18)",
          background: "rgba(255,255,255,0.04)",
        }}
      >
        <span style={{ paddingLeft: "14px", color: "rgba(0,212,255,0.45)", flexShrink: 0 }}>
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="11" cy="11" r="8" />
            <path d="m21 21-4.3-4.3" />
          </svg>
        </span>
        <input
          type="text"
          placeholder={placeholder}
          style={{
            flex: 1,
            border: "none",
            background: "transparent",
            padding: "11px 12px",
            fontSize: "0.875rem",
            color: "#fff",
            outline: "none",
          }}
          value={searchText}
          onFocus={() => setSearchDropdownOpen(true)}
          onChange={(e) => {
            setSearchText(e.target.value);
            if (!searchDropdownOpen) setSearchDropdownOpen(true);
          }}
        />
        {searchText.trim() && (
          <button
            type="button"
            onClick={() => {
              setSearchText("");
              setSearchSuggestions([]);
              setSearchDropdownOpen(true);
            }}
            style={{
              marginRight: "8px",
              width: "22px",
              height: "22px",
              borderRadius: "50%",
              border: "none",
              background: "rgba(255,255,255,0.1)",
              color: "#aaa",
              fontSize: "0.65rem",
              cursor: "pointer",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              flexShrink: 0,
            }}
            aria-label="Clear search"
          >
            ×
          </button>
        )}
        <button
          type="submit"
          disabled={searchSubmitPending || !searchText.trim()}
          style={{
            padding: "11px 20px",
            border: "none",
            background: "linear-gradient(135deg, #00d4ff, #7c3aed)",
            color: "#fff",
            fontWeight: 700,
            fontSize: "0.8rem",
            cursor: searchSubmitPending || !searchText.trim() ? "not-allowed" : "pointer",
            opacity: !searchText.trim() ? 0.6 : 1,
            transition: "opacity 0.2s",
            whiteSpace: "nowrap",
          }}
        >
          Search
        </button>
      </form>

      {searchDropdownOpen && (
        <div
          style={{
            position: "absolute",
            left: 0,
            right: 0,
            zIndex: 40,
            marginTop: "6px",
            borderRadius: "12px",
            border: "1px solid rgba(0,212,255,0.15)",
            background: "#111128",
            boxShadow: "0 16px 48px rgba(0,0,0,0.7)",
            overflow: "hidden",
          }}
        >
          {searchText.trim().length < 2 && (
            <p style={{ padding: "14px 16px", fontSize: "0.8rem", color: "#6868a0", margin: 0 }}>
              Type at least 2 characters to search.
            </p>
          )}
          {searchText.trim().length >= 2 && (
            <>
              <button
                type="button"
                onClick={() => openSearchResults(searchText)}
                style={{
                  width: "100%",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "space-between",
                  padding: "12px 16px",
                  textAlign: "left",
                  fontSize: "0.875rem",
                  color: "#fff",
                  background: "transparent",
                  border: "none",
                  borderBottom: "1px solid rgba(0,212,255,0.08)",
                  cursor: "pointer",
                  fontWeight: 600,
                }}
              >
                <span>Search for &quot;{searchText.trim()}&quot;</span>
                <span style={{ fontSize: "0.7rem", color: "#6868a0" }}>Enter ↵</span>
              </button>
              {searchSuggestionsLoading && (
                <p style={{ padding: "12px 16px", fontSize: "0.8rem", color: "#6868a0", margin: 0 }}>
                  Loading suggestions...
                </p>
              )}
              {!searchSuggestionsLoading && searchSuggestions.length === 0 && (
                <p style={{ padding: "12px 16px", fontSize: "0.8rem", color: "#6868a0", margin: 0 }}>
                  No matching products.
                </p>
              )}
              {!searchSuggestionsLoading && searchSuggestions.length > 0 && (
                <div style={{ maxHeight: "280px", overflowY: "auto" }}>
                  {searchSuggestions.map((product) => (
                    <button
                      key={product.id}
                      type="button"
                      onClick={() => openSuggestedProduct(product)}
                      style={{
                        display: "block",
                        width: "100%",
                        borderTop: "1px solid rgba(0,212,255,0.06)",
                        padding: "10px 16px",
                        textAlign: "left",
                        background: "transparent",
                        borderLeft: "none",
                        borderRight: "none",
                        borderBottom: "none",
                        cursor: "pointer",
                        transition: "background 0.12s",
                      }}
                      onMouseEnter={(e) => {
                        e.currentTarget.style.background = "rgba(0,212,255,0.05)";
                      }}
                      onMouseLeave={(e) => {
                        e.currentTarget.style.background = "transparent";
                      }}
                    >
                      <p style={{ margin: 0, fontSize: "0.875rem", fontWeight: 600, color: "#fff", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                        {product.name}
                      </p>
                      <p style={{ margin: 0, fontSize: "0.72rem", color: "#6868a0", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                        SKU: {product.sku}
                      </p>
                    </button>
                  ))}
                </div>
              )}
            </>
          )}
        </div>
      )}
    </div>
  );
}
