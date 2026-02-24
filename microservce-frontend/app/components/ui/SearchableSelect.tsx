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

  const inputStyle: React.CSSProperties = {
    width: "100%",
    padding: "9px 32px 9px 12px",
    borderRadius: 10,
    border: "1px solid var(--line)",
    background: "var(--surface-2)",
    color: "var(--ink)",
    fontSize: "0.82rem",
    outline: "none",
    transition: "border-color 0.15s",
  };

  return (
    <div ref={containerRef} style={{ position: "relative" }}>
      {value && selectedLabel && !open ? (
        <div
          style={{
            ...inputStyle,
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            cursor: disabled ? "not-allowed" : "pointer",
            opacity: disabled ? 0.6 : 1,
          }}
          onClick={() => {
            if (!disabled) {
              setOpen(true);
              setTimeout(() => inputRef.current?.focus(), 0);
            }
          }}
        >
          <span style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{selectedLabel}</span>
          {!disabled && (
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                handleClear();
              }}
              style={{
                width: 18,
                height: 18,
                borderRadius: "50%",
                background: "rgba(255,255,255,0.08)",
                border: "none",
                color: "var(--muted)",
                fontSize: "0.7rem",
                cursor: "pointer",
                display: "grid",
                placeItems: "center",
                flexShrink: 0,
              }}
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
          style={{ ...inputStyle, opacity: disabled ? 0.6 : 1, cursor: disabled ? "not-allowed" : "text" }}
        />
      )}

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
          {!loading && results.length === 0 && (
            <div style={{ padding: "16px 12px", textAlign: "center", fontSize: "0.78rem", color: "var(--muted)" }}>
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
                  style={{
                    padding: "8px 10px",
                    borderRadius: 8,
                    fontSize: "0.82rem",
                    cursor: "pointer",
                    color: isSelected ? "var(--brand)" : "var(--ink)",
                    fontWeight: isSelected ? 700 : 500,
                    background: isHighlighted
                      ? "var(--brand-soft)"
                      : isSelected
                        ? "rgba(0,212,255,0.06)"
                        : "transparent",
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
