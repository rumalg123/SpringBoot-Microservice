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
  values: string[];
  onChange: (values: string[], items: Record<string, unknown>[]) => void;
  disabled?: boolean;
};

export default function MultiSearchSelect({
  apiClient,
  endpoint,
  searchParam = "q",
  labelField,
  valueField = "id",
  placeholder = "Search to add...",
  values,
  onChange,
  disabled = false,
}: Props) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<Record<string, unknown>[]>([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [highlightIndex, setHighlightIndex] = useState(-1);
  const [selectedItems, setSelectedItems] = useState<Record<string, unknown>[]>([]);

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

  // Sync selectedItems when results change (to resolve labels)
  useEffect(() => {
    if (results.length === 0) return;
    setSelectedItems((prev) => {
      const updated = [...prev];
      for (const item of results) {
        const itemVal = String(item[valueField] || "");
        if (values.includes(itemVal) && !updated.some((s) => String(s[valueField] || "") === itemVal)) {
          updated.push(item);
        }
      }
      return updated.filter((s) => values.includes(String(s[valueField] || "")));
    });
  }, [results, values, valueField]);

  const handleInputChange = (text: string) => {
    setQuery(text);
    setOpen(true);
    fetchResults(text);
  };

  const handleSelect = (item: Record<string, unknown>) => {
    const itemValue = String(item[valueField] || "");
    if (values.includes(itemValue)) return; // already selected
    const newItems = [...selectedItems, item];
    const newValues = [...values, itemValue];
    setSelectedItems(newItems);
    onChange(newValues, newItems);
    setQuery("");
    fetchResults("");
    inputRef.current?.focus();
  };

  const handleRemove = (removeValue: string) => {
    const newItems = selectedItems.filter((s) => String(s[valueField] || "") !== removeValue);
    const newValues = values.filter((v) => v !== removeValue);
    setSelectedItems(newItems);
    onChange(newValues, newItems);
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (!open) {
      if (e.key === "ArrowDown" || e.key === "Enter") {
        setOpen(true);
        fetchResults(query);
      }
      // Backspace on empty input removes last chip
      if (e.key === "Backspace" && !query && values.length > 0) {
        handleRemove(values[values.length - 1]);
      }
      return;
    }
    const filteredResults = results.filter((item) => !values.includes(String(item[valueField] || "")));
    switch (e.key) {
      case "ArrowDown":
        e.preventDefault();
        setHighlightIndex((prev) => (prev < filteredResults.length - 1 ? prev + 1 : 0));
        break;
      case "ArrowUp":
        e.preventDefault();
        setHighlightIndex((prev) => (prev > 0 ? prev - 1 : filteredResults.length - 1));
        break;
      case "Enter":
        e.preventDefault();
        if (highlightIndex >= 0 && highlightIndex < filteredResults.length) {
          handleSelect(filteredResults[highlightIndex]);
        }
        break;
      case "Escape":
        e.preventDefault();
        setOpen(false);
        break;
      case "Backspace":
        if (!query && values.length > 0) {
          handleRemove(values[values.length - 1]);
        }
        break;
    }
  };

  const getLabel = (val: string): string => {
    const item = selectedItems.find((s) => String(s[valueField] || "") === val);
    return item ? String(item[labelField] || val) : val;
  };

  const filteredResults = results.filter((item) => !values.includes(String(item[valueField] || "")));

  return (
    <div ref={containerRef} className="relative">
      <div
        className={`flex flex-wrap items-center gap-1.5 py-1.5 px-2.5 rounded-md border border-line bg-surface-2 min-h-[40px] transition-colors duration-150 ${disabled ? "cursor-not-allowed opacity-60" : "cursor-text"}`}
        onClick={() => {
          if (!disabled) inputRef.current?.focus();
        }}
      >
        {values.map((val) => (
          <span key={val} className="inline-flex items-center gap-1 py-[3px] px-2 rounded-sm bg-brand-soft border border-line-bright text-brand text-[0.74rem] font-semibold whitespace-nowrap">
            <span className="overflow-hidden text-ellipsis max-w-[160px]">{getLabel(val)}</span>
            {!disabled && (
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  handleRemove(val);
                }}
                className="w-4 h-4 rounded-full bg-[rgba(255,255,255,0.08)] border-none text-brand text-[0.65rem] cursor-pointer grid place-items-center shrink-0 leading-none"
              >
                x
              </button>
            )}
          </span>
        ))}
        <input
          ref={inputRef}
          value={query}
          onChange={(e) => handleInputChange(e.target.value)}
          onFocus={() => setOpen(true)}
          onKeyDown={handleKeyDown}
          placeholder={values.length === 0 ? placeholder : ""}
          disabled={disabled}
          className="flex-1 min-w-[80px] border-none bg-transparent text-ink text-sm outline-none py-[3px]"
        />
      </div>

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
          {!loading && filteredResults.length === 0 && (
            <div className="py-4 px-3 text-center text-sm text-muted">
              {results.length > 0 && filteredResults.length === 0 ? "All results already selected" : "No results found"}
            </div>
          )}
          {!loading &&
            filteredResults.map((item, index) => {
              const itemValue = String(item[valueField] || "");
              const itemLabel = String(item[labelField] || "");
              const isHighlighted = index === highlightIndex;
              return (
                <div
                  key={itemValue || index}
                  onClick={() => handleSelect(item)}
                  onMouseEnter={() => setHighlightIndex(index)}
                  className={`py-2 px-2.5 rounded-[8px] text-sm cursor-pointer text-ink font-medium transition-[background] duration-100 ${isHighlighted ? "bg-brand-soft" : "bg-transparent"}`}
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
