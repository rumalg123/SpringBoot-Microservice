"use client";

import { FormEvent, useState } from "react";

type Category = {
  id: string;
  name: string;
  type: "PARENT" | "SUB";
  parentCategoryId: string | null;
};

type Props = {
  parents: Category[];
  subsByParent: Map<string, Category[]>;
  selectedParentNames: string[];
  selectedSubNames: string[];
  expandedParentIds: Record<string, boolean>;
  minPriceInput: string;
  maxPriceInput: string;
  loading: boolean;
  onMinPriceChange: (value: string) => void;
  onMaxPriceChange: (value: string) => void;
  onApplyPriceFilter: (event: FormEvent) => void;
  onClearPriceFilter: () => void;
  onToggleParent: (parent: Category) => void;
  onToggleSub: (sub: Category) => void;
  onToggleParentExpanded: (parentId: string) => void;
};

export default function CatalogFiltersSidebar({
  parents,
  subsByParent,
  selectedParentNames,
  selectedSubNames,
  expandedParentIds,
  minPriceInput,
  maxPriceInput,
  loading,
  onMinPriceChange,
  onMaxPriceChange,
  onApplyPriceFilter,
  onClearPriceFilter,
  onToggleParent,
  onToggleSub,
  onToggleParentExpanded,
}: Props) {
  const activeCount =
    selectedParentNames.length + selectedSubNames.length + (minPriceInput || maxPriceInput ? 1 : 0);

  return (
    <aside
      className="filter-sidebar animate-rise"
      style={{ padding: "0", border: "1px solid rgba(0,212,255,0.1)" }}
    >
      {/* Sidebar Header */}
      <div
        style={{
          padding: "16px 18px",
          borderBottom: "1px solid rgba(0,212,255,0.08)",
          background: "rgba(0,212,255,0.03)",
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
        }}
      >
        <div className="flex items-center gap-2">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#00d4ff" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
            <polygon points="22 3 2 3 10 12.46 10 19 14 21 14 12.46 22 3" />
          </svg>
          <span style={{ fontFamily: "'Syne', sans-serif", fontWeight: 800, fontSize: "0.9rem", color: "#fff" }}>
            Filters
          </span>
          {activeCount > 0 && (
            <span
              style={{
                background: "linear-gradient(135deg, #00d4ff, #7c3aed)",
                color: "#fff",
                fontSize: "0.6rem",
                fontWeight: 800,
                padding: "2px 7px",
                borderRadius: "20px",
                lineHeight: 1.4,
              }}
            >
              {activeCount}
            </span>
          )}
        </div>
      </div>

      <div style={{ padding: "16px 18px", display: "flex", flexDirection: "column", gap: "20px" }}>
        {/* Price Range */}
        <div>
          <p className="filter-section-title mb-3">Price Range</p>
          <form onSubmit={onApplyPriceFilter}>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "8px", marginBottom: "10px" }}>
              <div style={{ position: "relative" }}>
                <span
                  style={{
                    position: "absolute",
                    left: "10px",
                    top: "50%",
                    transform: "translateY(-50%)",
                    color: "rgba(0,212,255,0.5)",
                    fontSize: "0.75rem",
                    fontWeight: 700,
                    pointerEvents: "none",
                  }}
                >
                  $
                </span>
                <input
                  type="number"
                  min="0"
                  step="0.01"
                  value={minPriceInput}
                  onChange={(e) => onMinPriceChange(e.target.value)}
                  placeholder="Min"
                  style={{
                    width: "100%",
                    padding: "9px 10px 9px 22px",
                    borderRadius: "8px",
                    border: "1px solid rgba(0,212,255,0.15)",
                    background: "rgba(0,212,255,0.04)",
                    color: "#fff",
                    fontSize: "0.8rem",
                    outline: "none",
                  }}
                />
              </div>
              <div style={{ position: "relative" }}>
                <span
                  style={{
                    position: "absolute",
                    left: "10px",
                    top: "50%",
                    transform: "translateY(-50%)",
                    color: "rgba(0,212,255,0.5)",
                    fontSize: "0.75rem",
                    fontWeight: 700,
                    pointerEvents: "none",
                  }}
                >
                  $
                </span>
                <input
                  type="number"
                  min="0"
                  step="0.01"
                  value={maxPriceInput}
                  onChange={(e) => onMaxPriceChange(e.target.value)}
                  placeholder="Max"
                  style={{
                    width: "100%",
                    padding: "9px 10px 9px 22px",
                    borderRadius: "8px",
                    border: "1px solid rgba(0,212,255,0.15)",
                    background: "rgba(0,212,255,0.04)",
                    color: "#fff",
                    fontSize: "0.8rem",
                    outline: "none",
                  }}
                />
              </div>
            </div>
            <div style={{ display: "flex", gap: "8px" }}>
              <button
                type="submit"
                disabled={loading}
                style={{
                  flex: 1,
                  padding: "8px",
                  borderRadius: "8px",
                  border: "none",
                  background: "linear-gradient(135deg, #00d4ff, #7c3aed)",
                  color: "#fff",
                  fontSize: "0.75rem",
                  fontWeight: 700,
                  cursor: loading ? "not-allowed" : "pointer",
                  opacity: loading ? 0.5 : 1,
                  transition: "all 0.2s",
                }}
              >
                Apply
              </button>
              <button
                type="button"
                disabled={loading}
                onClick={onClearPriceFilter}
                style={{
                  flex: 1,
                  padding: "8px",
                  borderRadius: "8px",
                  border: "1px solid rgba(0,212,255,0.2)",
                  background: "transparent",
                  color: "#00d4ff",
                  fontSize: "0.75rem",
                  fontWeight: 700,
                  cursor: loading ? "not-allowed" : "pointer",
                  opacity: loading ? 0.5 : 1,
                  transition: "all 0.2s",
                }}
              >
                Clear
              </button>
            </div>
          </form>
        </div>

        {/* Divider */}
        <div className="neon-divider" />

        {/* Categories */}
        <div>
          <p className="filter-section-title mb-3">Categories</p>

          {parents.length === 0 && (
            <p style={{ fontSize: "0.78rem", color: "#4a4a70", padding: "8px 10px", background: "rgba(0,212,255,0.03)", borderRadius: "8px" }}>
              No categories available.
            </p>
          )}

          <div style={{ display: "flex", flexDirection: "column", gap: "6px" }}>
            {parents.map((parent) => {
              const selected = selectedParentNames.includes(parent.name);
              const expanded = Boolean(expandedParentIds[parent.id]);
              const subs = subsByParent.get(parent.id) || [];

              return (
                <div
                  key={parent.id}
                  style={{
                    borderRadius: "10px",
                    border: selected ? "1px solid rgba(0,212,255,0.35)" : "1px solid rgba(0,212,255,0.08)",
                    background: selected ? "rgba(0,212,255,0.05)" : "transparent",
                    overflow: "hidden",
                    transition: "all 0.2s",
                  }}
                >
                  <div
                    style={{
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "space-between",
                      padding: "9px 12px",
                      gap: "8px",
                    }}
                  >
                    <label
                      style={{
                        display: "flex",
                        alignItems: "center",
                        gap: "10px",
                        fontSize: "0.82rem",
                        color: selected ? "#00d4ff" : "#c8c8e8",
                        cursor: "pointer",
                        flex: 1,
                        fontWeight: selected ? 700 : 500,
                      }}
                    >
                      <input
                        type="checkbox"
                        checked={selected}
                        onChange={() => onToggleParent(parent)}
                        style={{ accentColor: "#00d4ff", width: "14px", height: "14px" }}
                      />
                      {parent.name}
                      {selected && (
                        <span
                          style={{
                            fontSize: "0.6rem",
                            background: "rgba(0,212,255,0.15)",
                            color: "#00d4ff",
                            padding: "1px 6px",
                            borderRadius: "10px",
                            fontWeight: 800,
                          }}
                        >
                          ✓
                        </span>
                      )}
                    </label>
                    {subs.length > 0 && (
                      <button
                        type="button"
                        onClick={() => onToggleParentExpanded(parent.id)}
                        style={{
                          width: "22px",
                          height: "22px",
                          borderRadius: "6px",
                          border: "1px solid rgba(0,212,255,0.15)",
                          background: "rgba(0,212,255,0.05)",
                          color: "#00d4ff",
                          fontSize: "0.7rem",
                          fontWeight: 800,
                          cursor: "pointer",
                          display: "flex",
                          alignItems: "center",
                          justifyContent: "center",
                          flexShrink: 0,
                          transition: "all 0.2s",
                        }}
                        aria-label={expanded ? "Collapse" : "Expand"}
                      >
                        {expanded ? "−" : "+"}
                      </button>
                    )}
                  </div>

                  {expanded && subs.length > 0 && (
                    <div
                      style={{
                        borderTop: "1px solid rgba(0,212,255,0.08)",
                        padding: "8px 12px 10px 32px",
                        display: "flex",
                        flexDirection: "column",
                        gap: "7px",
                        background: "rgba(0,0,0,0.2)",
                      }}
                    >
                      {subs.map((sub) => {
                        const subSelected = selectedSubNames.includes(sub.name);
                        return (
                          <label
                            key={sub.id}
                            style={{
                              display: "flex",
                              alignItems: "center",
                              gap: "8px",
                              fontSize: "0.78rem",
                              color: subSelected ? "#00d4ff" : "#6868a0",
                              cursor: "pointer",
                              fontWeight: subSelected ? 600 : 400,
                              transition: "color 0.15s",
                            }}
                          >
                            <input
                              type="checkbox"
                              checked={subSelected}
                              onChange={() => onToggleSub(sub)}
                              style={{ accentColor: "#00d4ff", width: "12px", height: "12px" }}
                            />
                            {sub.name}
                          </label>
                        );
                      })}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </aside>
  );
}
