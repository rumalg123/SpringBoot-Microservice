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
    <div ref={searchBoxRef} className={`relative ${className ?? ""}`} style={{ maxWidth, ...style }}>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          if (searchSubmitPending) return;
          openSearchResults(searchText);
        }}
        className="flex items-center overflow-hidden rounded-xl border border-brand/[0.18] bg-white/[0.04]"
      >
        <span className="pl-3.5 text-brand/45 shrink-0">
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="11" cy="11" r="8" />
            <path d="m21 21-4.3-4.3" />
          </svg>
        </span>
        <input
          type="text"
          placeholder={placeholder}
          className="flex-1 border-none bg-transparent px-3 py-[11px] text-base text-white outline-none"
          value={searchText}
          role="combobox"
          aria-expanded={searchDropdownOpen}
          aria-autocomplete="list"
          aria-controls="search-suggestions-listbox"
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
            className="mr-2 w-[22px] h-[22px] rounded-full border-none bg-white/10 text-[#aaa] text-[0.65rem] cursor-pointer flex items-center justify-center shrink-0"
            aria-label="Clear search"
          >
            ×
          </button>
        )}
        <button
          type="submit"
          disabled={searchSubmitPending || !searchText.trim()}
          className={`px-5 py-[11px] border-none bg-[image:linear-gradient(135deg,#00d4ff,#7c3aed)] text-white font-bold text-sm transition-opacity duration-200 whitespace-nowrap ${searchSubmitPending || !searchText.trim() ? "cursor-not-allowed opacity-60" : "cursor-pointer opacity-100"}`}
        >
          Search
        </button>
      </form>

      {searchDropdownOpen && (
        <div
          id="search-suggestions-listbox"
          role="listbox"
          aria-live="polite"
          className="absolute left-0 right-0 z-40 mt-1.5 rounded-xl border border-brand/15 bg-[#111128] shadow-[0_16px_48px_rgba(0,0,0,0.7)] overflow-hidden"
        >
          {/* Empty state: show popular searches */}
          {searchText.trim().length < 1 && popularSearches.length > 0 && (
            <div className="px-4 py-3">
              <p className="mb-2 text-xs text-[#6868a0] font-semibold uppercase tracking-wide">
                Popular Searches
              </p>
              <div className="flex flex-wrap gap-1.5">
                {popularSearches.slice(0, 8).map((term) => (
                  <button
                    key={term}
                    type="button"
                    role="option"
                    onClick={() => openSearchResults(term)}
                    className="px-3 py-[5px] rounded-2xl border border-brand/[0.12] bg-brand/[0.06] text-[#c0c0e0] text-[0.78rem] cursor-pointer"
                  >
                    {term}
                  </button>
                ))}
              </div>
            </div>
          )}

          {searchText.trim().length < 1 && popularSearches.length === 0 && (
            <p className="px-4 py-3.5 text-sm text-[#6868a0] m-0">
              Type to search products...
            </p>
          )}

          {searchText.trim().length >= 1 && (
            <>
              <button
                type="button"
                role="option"
                onClick={() => openSearchResults(searchText)}
                className="w-full flex items-center justify-between px-4 py-3 text-left text-base text-white bg-transparent border-none border-b border-brand/[0.08] cursor-pointer font-semibold"
              >
                <span>Search for &quot;{searchText.trim()}&quot;</span>
                <span className="text-xs text-[#6868a0]">Enter ↵</span>
              </button>

              {suggestionsLoading && (
                <p className="px-4 py-3 text-sm text-[#6868a0] m-0">
                  Loading suggestions...
                </p>
              )}

              {!suggestionsLoading && suggestions.length === 0 && searchText.trim().length >= 2 && (
                <p className="px-4 py-3 text-sm text-[#6868a0] m-0">
                  No matching products.
                </p>
              )}

              {!suggestionsLoading && (
                <div className="max-h-[320px] overflow-y-auto">
                  {/* Query suggestions */}
                  {querySuggestions.length > 0 && (
                    <>
                      {querySuggestions.map((s) => (
                        <button
                          key={`q-${s.text}`}
                          type="button"
                          role="option"
                          onClick={() => openSearchResults(s.text)}
                          className="flex items-center gap-2.5 w-full border-t border-brand/[0.06] border-l-0 border-r-0 border-b-0 px-4 py-2.5 text-left bg-transparent cursor-pointer transition-colors duration-[120ms] hover:bg-brand/[0.05]"
                        >
                          <svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="#6868a0" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                            <circle cx="11" cy="11" r="8" /><path d="m21 21-4.3-4.3" />
                          </svg>
                          <span className="text-[0.84rem] text-[#d0d0e8]">{s.text}</span>
                        </button>
                      ))}
                    </>
                  )}

                  {/* Product suggestions */}
                  {productSuggestions.length > 0 && (
                    <>
                      {querySuggestions.length > 0 && (
                        <div className="border-t border-brand/[0.08] px-4 pt-1.5 pb-0.5">
                          <p className="m-0 text-[0.65rem] text-[#6868a0] font-semibold uppercase tracking-wide">
                            Products
                          </p>
                        </div>
                      )}
                      {productSuggestions.map((s) => (
                        <button
                          key={`p-${s.id}`}
                          type="button"
                          role="option"
                          onClick={() => openSuggestion(s)}
                          className="block w-full border-t border-brand/[0.06] border-l-0 border-r-0 border-b-0 px-4 py-2.5 text-left bg-transparent cursor-pointer transition-colors duration-[120ms] hover:bg-brand/[0.05]"
                        >
                          <p className="m-0 text-base font-semibold text-white overflow-hidden text-ellipsis whitespace-nowrap">
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
