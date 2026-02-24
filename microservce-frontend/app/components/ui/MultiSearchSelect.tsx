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

  const containerStyle: React.CSSProperties = {
    position: "relative",
  };

  const wrapperStyle: React.CSSProperties = {
    display: "flex",
    flexWrap: "wrap",
    alignItems: "center",
    gap: 6,
    padding: "6px 10px",
    borderRadius: 10,
    border: "1px solid var(--line)",
    background: "var(--surface-2)",
    minHeight: 40,
    cursor: disabled ? "not-allowed" : "text",
    opacity: disabled ? 0.6 : 1,
    transition: "border-color 0.15s",
  };

  const chipStyle: React.CSSProperties = {
    display: "inline-flex",
    alignItems: "center",
    gap: 4,
    padding: "3px 8px",
    borderRadius: 6,
    background: "var(--brand-soft)",
    border: "1px solid var(--line-bright)",
    color: "var(--brand)",
    fontSize: "0.74rem",
    fontWeight: 600,
    whiteSpace: "nowrap",
  };

  const chipCloseStyle: React.CSSProperties = {
    width: 16,
    height: 16,
    borderRadius: "50%",
    background: "rgba(255,255,255,0.08)",
    border: "none",
    color: "var(--brand)",
    fontSize: "0.65rem",
    cursor: "pointer",
    display: "grid",
    placeItems: "center",
    flexShrink: 0,
    lineHeight: 1,
  };

  return (
    <div ref={containerRef} style={containerStyle}>
      <div
        style={wrapperStyle}
        onClick={() => {
          if (!disabled) inputRef.current?.focus();
        }}
      >
        {values.map((val) => (
          <span key={val} style={chipStyle}>
            <span style={{ overflow: "hidden", textOverflow: "ellipsis", maxWidth: 160 }}>{getLabel(val)}</span>
            {!disabled && (
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  handleRemove(val);
                }}
                style={chipCloseStyle}
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
          style={{
            flex: 1,
            minWidth: 80,
            border: "none",
            background: "transparent",
            color: "var(--ink)",
            fontSize: "0.82rem",
            outline: "none",
            padding: "3px 0",
          }}
        />
      </div>

      {/* Dropdown */}
      {open && !disabled && (
        <div
          ref={listRef}
          style={{
            position: "absolute",
            top: "calc(100% + 4px)",
            left: 0,
            right: 0,
            maxHeight: 220,
            overflowY: "auto",
            borderRadius: 10,
            border: "1px solid var(--line-bright)",
            background: "var(--surface-2)",
            boxShadow: "0 8px 32px rgba(0,0,0,0.5)",
            zIndex: 50,
            padding: 4,
          }}
        >
          {loading && (
            <div style={{ display: "flex", alignItems: "center", justifyContent: "center", padding: "16px 12px", gap: 8 }}>
              <span
                style={{
                  display: "inline-block",
                  width: 14,
                  height: 14,
                  border: "2px solid var(--brand)",
                  borderTopColor: "transparent",
                  borderRadius: "50%",
                  animation: "spin 0.6s linear infinite",
                }}
              />
              <span style={{ fontSize: "0.78rem", color: "var(--muted)" }}>Searching...</span>
            </div>
          )}
          {!loading && filteredResults.length === 0 && (
            <div style={{ padding: "16px 12px", textAlign: "center", fontSize: "0.78rem", color: "var(--muted)" }}>
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
                  style={{
                    padding: "8px 10px",
                    borderRadius: 8,
                    fontSize: "0.82rem",
                    cursor: "pointer",
                    color: "var(--ink)",
                    fontWeight: 500,
                    background: isHighlighted ? "var(--brand-soft)" : "transparent",
                    transition: "background 0.1s",
                  }}
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
