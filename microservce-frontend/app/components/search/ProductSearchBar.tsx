"use client";

import { CSSProperties, useEffect, useRef, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import type { AutocompleteSuggestion, AutocompleteResponse } from "../../../lib/types";
import { API_BASE } from "../../../lib/constants";

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
  const [suggestions, setSuggestions] = useState<AutocompleteSuggestion[]>([]);
  const [popularSearches, setPopularSearches] = useState<string[]>([]);
  const [suggestionsLoading, setSuggestionsLoading] = useState(false);
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

  // Fetch popular searches on mount so they're ready when the user focuses
  useEffect(() => {
    const controller = new AbortController();
    const run = async () => {
      try {
        const res = await fetch(`${API_BASE}/search/popular`, { cache: "no-store", signal: controller.signal });
        if (!res.ok) return;
        if (controller.signal.aborted) return;
        const data = (await res.json()) as { searches: string[] };
        setPopularSearches(data.searches || []);
      } catch { /* ignore */ }
    };
    void run();
    return () => controller.abort();
  }, []);

  useEffect(() => {
    const term = searchText.trim();
    if (term.length < 1) {
      setSuggestions([]);
      setSuggestionsLoading(false);
      return;
    }

    const controller = new AbortController();
    const timer = window.setTimeout(async () => {
      setSuggestionsLoading(true);
      try {
        const params = new URLSearchParams({ prefix: term, limit: "8" });
        const res = await fetch(`${API_BASE}/search/autocomplete?${params.toString()}`, { cache: "no-store", signal: controller.signal });
        if (!res.ok) throw new Error("Failed");
        const data = (await res.json()) as AutocompleteResponse;
        if (controller.signal.aborted) return;
        setSuggestions(data.suggestions || []);
        setPopularSearches(data.popularSearches || []);
      } catch {
        if (controller.signal.aborted) return;
        setSuggestions([]);
        setPopularSearches([]);
      } finally {
        if (!controller.signal.aborted) setSuggestionsLoading(false);
      }
    }, 200);

    return () => {
      controller.abort();
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

  const openSuggestion = (suggestion: AutocompleteSuggestion) => {
    setSearchDropdownOpen(false);
    if (suggestion.type === "product" && suggestion.slug) {
      router.push(`/products/${encodeURIComponent(suggestion.slug.trim())}`);
    } else {
      openSearchResults(suggestion.text);
    }
  };

  const productSuggestions = suggestions.filter((s) => s.type === "product");
  const querySuggestions = suggestions.filter((s) => s.type === "query");

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
              setSuggestions([]);
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
          {/* Empty state: show popular searches */}
          {searchText.trim().length < 1 && popularSearches.length > 0 && (
            <div style={{ padding: "12px 16px" }}>
              <p style={{ margin: "0 0 8px", fontSize: "0.7rem", color: "#6868a0", fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.05em" }}>
                Popular Searches
              </p>
              <div style={{ display: "flex", flexWrap: "wrap", gap: "6px" }}>
                {popularSearches.slice(0, 8).map((term) => (
                  <button
                    key={term}
                    type="button"
                    onClick={() => openSearchResults(term)}
                    style={{
                      padding: "5px 12px",
                      borderRadius: "16px",
                      border: "1px solid rgba(0,212,255,0.12)",
                      background: "rgba(0,212,255,0.06)",
                      color: "#c0c0e0",
                      fontSize: "0.78rem",
                      cursor: "pointer",
                    }}
                  >
                    {term}
                  </button>
                ))}
              </div>
            </div>
          )}

          {searchText.trim().length < 1 && popularSearches.length === 0 && (
            <p style={{ padding: "14px 16px", fontSize: "0.8rem", color: "#6868a0", margin: 0 }}>
              Type to search products...
            </p>
          )}

          {searchText.trim().length >= 1 && (
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

              {suggestionsLoading && (
                <p style={{ padding: "12px 16px", fontSize: "0.8rem", color: "#6868a0", margin: 0 }}>
                  Loading suggestions...
                </p>
              )}

              {!suggestionsLoading && suggestions.length === 0 && searchText.trim().length >= 2 && (
                <p style={{ padding: "12px 16px", fontSize: "0.8rem", color: "#6868a0", margin: 0 }}>
                  No matching products.
                </p>
              )}

              {!suggestionsLoading && (
                <div style={{ maxHeight: "320px", overflowY: "auto" }}>
                  {/* Query suggestions */}
                  {querySuggestions.length > 0 && (
                    <>
                      {querySuggestions.map((s) => (
                        <button
                          key={`q-${s.text}`}
                          type="button"
                          onClick={() => openSearchResults(s.text)}
                          style={{
                            display: "flex",
                            alignItems: "center",
                            gap: "10px",
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
                          onMouseEnter={(e) => { e.currentTarget.style.background = "rgba(0,212,255,0.05)"; }}
                          onMouseLeave={(e) => { e.currentTarget.style.background = "transparent"; }}
                        >
                          <svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="#6868a0" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                            <circle cx="11" cy="11" r="8" /><path d="m21 21-4.3-4.3" />
                          </svg>
                          <span style={{ fontSize: "0.84rem", color: "#d0d0e8" }}>{s.text}</span>
                        </button>
                      ))}
                    </>
                  )}

                  {/* Product suggestions */}
                  {productSuggestions.length > 0 && (
                    <>
                      {querySuggestions.length > 0 && (
                        <div style={{ borderTop: "1px solid rgba(0,212,255,0.08)", padding: "6px 16px 2px" }}>
                          <p style={{ margin: 0, fontSize: "0.65rem", color: "#6868a0", fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.05em" }}>
                            Products
                          </p>
                        </div>
                      )}
                      {productSuggestions.map((s) => (
                        <button
                          key={`p-${s.id}`}
                          type="button"
                          onClick={() => openSuggestion(s)}
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
                          onMouseEnter={(e) => { e.currentTarget.style.background = "rgba(0,212,255,0.05)"; }}
                          onMouseLeave={(e) => { e.currentTarget.style.background = "transparent"; }}
                        >
                          <p style={{ margin: 0, fontSize: "0.875rem", fontWeight: 600, color: "#fff", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                            {s.text}
                          </p>
                        </button>
                      ))}
                    </>
                  )}
                </div>
              )}
            </>
          )}
        </div>
      )}
    </div>
  );
}
