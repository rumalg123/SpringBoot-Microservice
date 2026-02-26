"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import type { AxiosInstance } from "axios";

type Props = {
  apiClient: AxiosInstance | null;
  endpoint: string;
  searchParam?: string;
  labelField: string;
  valueField?: string;
  placeholder?: string;
  value: string;
  onChange: (value: string, item: Record<string, unknown>) => void;
  disabled?: boolean;
};

export default function SearchableSelect({
  apiClient,
  endpoint,
  searchParam = "q",
  labelField,
  valueField = "id",
  placeholder = "Search...",
  value,
  onChange,
  disabled = false,
}: Props) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<Record<string, unknown>[]>([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [highlightIndex, setHighlightIndex] = useState(-1);
  const [selectedLabel, setSelectedLabel] = useState("");

  const containerRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const listRef = useRef<HTMLDivElement>(null);

  // Fetch results with debounce
  const fetchResults = useCallback(
    (search: string) => {
      if (!apiClient) return;
      if (debounceRef.current) clearTimeout(debounceRef.current);
      debounceRef.current = setTimeout(async () => {
        setLoading(true);
        try {
          const res = await apiClient.get(endpoint, { params: { [searchParam]: search } });
          const data = res.data;
          const items = Array.isArray(data) ? data : Array.isArray(data?.content) ? data.content : [];
          setResults(items);
          setHighlightIndex(-1);
        } catch {
          setResults([]);
        } finally {
          setLoading(false);
        }
      }, 300);
    },
    [apiClient, endpoint, searchParam],
  );

  // Load initial results when opening
  useEffect(() => {
    if (open && results.length === 0 && !loading) {
      fetchResults(query);
    }
  }, [open]); // eslint-disable-line react-hooks/exhaustive-deps

  // Click outside to close
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, []);

  // Sync selected label when value changes externally
  useEffect(() => {
    if (!value) {
      setSelectedLabel("");
      return;
    }
    const match = results.find((item) => String(item[valueField]) === value);
    if (match) {
      setSelectedLabel(String(match[labelField] || ""));
    }
  }, [value, results, valueField, labelField]);

  const handleInputChange = (text: string) => {
    setQuery(text);
    setOpen(true);
    fetchResults(text);
  };

  const handleSelect = (item: Record<string, unknown>) => {
    const itemValue = String(item[valueField] || "");
    const itemLabel = String(item[labelField] || "");
    setSelectedLabel(itemLabel);
    setQuery("");
    setOpen(false);
    onChange(itemValue, item);
  };

  const handleClear = () => {
    setSelectedLabel("");
    setQuery("");
    setResults([]);
    onChange("", {} as Record<string, unknown>);
    inputRef.current?.focus();
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (!open) {
      if (e.key === "ArrowDown" || e.key === "Enter") {
        setOpen(true);
        fetchResults(query);
      }
      return;
    }
    switch (e.key) {
      case "ArrowDown":
        e.preventDefault();
        setHighlightIndex((prev) => {
          const next = prev < results.length - 1 ? prev + 1 : 0;
          scrollToIndex(next);
          return next;
        });
        break;
      case "ArrowUp":
        e.preventDefault();
        setHighlightIndex((prev) => {
          const next = prev > 0 ? prev - 1 : results.length - 1;
          scrollToIndex(next);
          return next;
        });
        break;
      case "Enter":
        e.preventDefault();
        if (highlightIndex >= 0 && highlightIndex < results.length) {
          handleSelect(results[highlightIndex]);
        }
        break;
      case "Escape":
        e.preventDefault();
        setOpen(false);
        break;
    }
  };

  const scrollToIndex = (index: number) => {
    if (!listRef.current) return;
    const children = listRef.current.children;
    if (children[index]) {
      (children[index] as HTMLElement).scrollIntoView({ block: "nearest" });
    }
  };

  return (
    <div ref={containerRef} className="relative">
      {value && selectedLabel && !open ? (
        <div
          className={`w-full py-[9px] pr-8 pl-3 rounded-md border border-line bg-surface-2 text-ink text-sm outline-none transition-colors duration-150 flex items-center justify-between ${disabled ? "cursor-not-allowed opacity-60" : "cursor-pointer"}`}
          onClick={() => {
            if (!disabled) {
              setOpen(true);
              setTimeout(() => inputRef.current?.focus(), 0);
            }
          }}
        >
          <span className="overflow-hidden text-ellipsis whitespace-nowrap">{selectedLabel}</span>
          {!disabled && (
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                handleClear();
              }}
              className="w-[18px] h-[18px] rounded-full bg-[rgba(255,255,255,0.08)] border-none text-muted text-xs cursor-pointer grid place-items-center shrink-0"
            >
              x
            </button>
          )}
        </div>
      ) : (
        <input
          ref={inputRef}
          value={query}
          onChange={(e) => handleInputChange(e.target.value)}
          onFocus={() => setOpen(true)}
          onKeyDown={handleKeyDown}
          placeholder={placeholder}
          disabled={disabled}
          className={`w-full py-[9px] pr-8 pl-3 rounded-md border border-line bg-surface-2 text-ink text-sm outline-none transition-colors duration-150 ${disabled ? "opacity-60 cursor-not-allowed" : "cursor-text"}`}
        />
      )}

      {/* Dropdown */}
      {open && !disabled && (
        <div
          ref={listRef}
          className="absolute top-[calc(100%+4px)] left-0 right-0 max-h-[220px] overflow-y-auto rounded-md border border-line-bright bg-surface-2 shadow-[0_8px_32px_rgba(0,0,0,0.5)] z-50 p-1"
        >
          {loading && (
            <div className="flex items-center justify-center py-4 px-3 gap-2">
              <span className="inline-block w-3.5 h-3.5 border-2 border-brand border-t-transparent rounded-full animate-spin" />
              <span className="text-sm text-muted">Searching...</span>
            </div>
          )}
          {!loading && results.length === 0 && (
            <div className="py-4 px-3 text-center text-sm text-muted">
              No results found
            </div>
          )}
          {!loading &&
            results.map((item, index) => {
              const itemValue = String(item[valueField] || "");
              const itemLabel = String(item[labelField] || "");
              const isHighlighted = index === highlightIndex;
              const isSelected = itemValue === value;
              return (
                <div
                  key={itemValue || index}
                  onClick={() => handleSelect(item)}
                  onMouseEnter={() => setHighlightIndex(index)}
                  className={`py-2 px-2.5 rounded-[8px] text-sm cursor-pointer font-medium transition-[background] duration-100 ${isSelected ? "text-brand font-bold" : "text-ink"} ${isHighlighted ? "bg-brand-soft" : isSelected ? "bg-[rgba(0,212,255,0.06)]" : "bg-transparent"}`}
                >
                  {itemLabel}
                </div>
              );
            })}
        </div>
      )}
    </div>
  );
}
