"use client";
import { ReactNode, useRef } from "react";
import Pagination from "../Pagination";

export type Column<T = Record<string, unknown>> = {
  key: string;
  header: string;
  sortable?: boolean;
  render?: (value: unknown, row: T) => ReactNode;
  width?: string;
};

type Props<T = Record<string, unknown>> = {
  columns: Column<T>[];
  data: T[];
  page: number;
  totalPages: number;
  totalElements?: number;
  onPageChange: (page: number) => void;
  loading?: boolean;
  emptyTitle?: string;
  emptyDescription?: string;
  selectable?: boolean;
  selectedIds?: Set<string>;
  onSelectionChange?: (ids: Set<string>) => void;
  idField?: string;
  renderActions?: (row: T) => ReactNode;
};

export default function DataTable<T extends Record<string, unknown> = Record<string, unknown>>({
  columns,
  data,
  page,
  totalPages,
  totalElements,
  onPageChange,
  loading = false,
  emptyTitle = "No data found",
  emptyDescription = "Try adjusting your filters or search criteria.",
  selectable = false,
  selectedIds,
  onSelectionChange,
  idField = "id",
  renderActions,
}: Props<T>) {
  const checkboxAllRef = useRef<HTMLInputElement>(null);

  const allIds = data.map((row) => String(row[idField] || ""));
  const allSelected = allIds.length > 0 && allIds.every((id) => selectedIds?.has(id));
  const someSelected = !allSelected && allIds.some((id) => selectedIds?.has(id));

  // Sync indeterminate state
  if (checkboxAllRef.current) {
    checkboxAllRef.current.indeterminate = someSelected;
  }

  const handleSelectAll = (checked: boolean) => {
    if (!onSelectionChange || !selectedIds) return;
    const next = new Set(selectedIds);
    if (checked) {
      allIds.forEach((id) => next.add(id));
    } else {
      allIds.forEach((id) => next.delete(id));
    }
    onSelectionChange(next);
  };

  const handleSelectRow = (rowId: string, checked: boolean) => {
    if (!onSelectionChange || !selectedIds) return;
    const next = new Set(selectedIds);
    if (checked) {
      next.add(rowId);
    } else {
      next.delete(rowId);
    }
    onSelectionChange(next);
  };

  const totalColumns = columns.length + (selectable ? 1 : 0) + (renderActions ? 1 : 0);

  const shimmerKeyframes = `
    @keyframes shimmer {
      0% { background-position: -400px 0; }
      100% { background-position: 400px 0; }
    }
  `;

  const skeletonBarStyle: React.CSSProperties = {
    height: 12,
    borderRadius: 6,
    background: "linear-gradient(90deg, var(--surface-3) 25%, rgba(255,255,255,0.06) 50%, var(--surface-3) 75%)",
    backgroundSize: "800px 100%",
    animation: "shimmer 1.5s ease-in-out infinite",
  };

  return (
    <div>
      <style>{shimmerKeyframes}</style>
      <div
        className="glass-card"
        style={{
          overflow: "hidden",
          borderRadius: 12,
          border: "1px solid var(--line)",
        }}
      >
        <div style={{ overflowX: "auto" }}>
          <table
            style={{
              width: "100%",
              borderCollapse: "collapse",
              minWidth: 700,
            }}
          >
            <thead>
              <tr>
                {selectable && (
                  <th
                    style={{
                      width: 44,
                      padding: "12px 10px",
                      position: "sticky",
                      top: 0,
                      background: "var(--surface-2)",
                      borderBottom: "1px solid var(--line)",
                      textAlign: "center",
                      zIndex: 2,
                    }}
                  >
                    <input
                      ref={checkboxAllRef}
                      type="checkbox"
                      checked={allSelected}
                      onChange={(e) => handleSelectAll(e.target.checked)}
                      disabled={loading || data.length === 0}
                      style={{ cursor: loading ? "not-allowed" : "pointer", accentColor: "var(--brand)" }}
                    />
                  </th>
                )}
                {columns.map((col) => (
                  <th
                    key={col.key}
                    style={{
                      padding: "12px 14px",
                      position: "sticky",
                      top: 0,
                      background: "var(--surface-2)",
                      borderBottom: "1px solid var(--line)",
                      textAlign: "left",
                      fontSize: "0.72rem",
                      fontWeight: 700,
                      color: "var(--muted)",
                      textTransform: "uppercase",
                      letterSpacing: "0.06em",
                      whiteSpace: "nowrap",
                      width: col.width || "auto",
                      zIndex: 2,
                    }}
                  >
                    {col.header}
                  </th>
                ))}
                {renderActions && (
                  <th
                    style={{
                      padding: "12px 14px",
                      position: "sticky",
                      top: 0,
                      background: "var(--surface-2)",
                      borderBottom: "1px solid var(--line)",
                      textAlign: "right",
                      fontSize: "0.72rem",
                      fontWeight: 700,
                      color: "var(--muted)",
                      textTransform: "uppercase",
                      letterSpacing: "0.06em",
                      whiteSpace: "nowrap",
                      zIndex: 2,
                    }}
                  >
                    Actions
                  </th>
                )}
              </tr>
            </thead>
            <tbody>
              {/* Loading skeleton */}
              {loading &&
                Array.from({ length: 6 }).map((_, rowIdx) => (
                  <tr key={`skeleton-${rowIdx}`}>
                    {selectable && (
                      <td style={{ padding: "14px 10px", borderBottom: "1px solid var(--line)", textAlign: "center" }}>
                        <div style={{ ...skeletonBarStyle, width: 16, height: 16, borderRadius: 4, margin: "0 auto" }} />
                      </td>
                    )}
                    {columns.map((col) => (
                      <td key={col.key} style={{ padding: "14px 14px", borderBottom: "1px solid var(--line)" }}>
                        <div style={{ ...skeletonBarStyle, width: `${55 + Math.random() * 35}%` }} />
                      </td>
                    ))}
                    {renderActions && (
                      <td style={{ padding: "14px 14px", borderBottom: "1px solid var(--line)", textAlign: "right" }}>
                        <div style={{ ...skeletonBarStyle, width: 60, marginLeft: "auto" }} />
                      </td>
                    )}
                  </tr>
                ))}

              {/* Empty state */}
              {!loading && data.length === 0 && (
                <tr>
                  <td colSpan={totalColumns} style={{ padding: 0 }}>
                    <div style={{ textAlign: "center", padding: "60px 24px", color: "var(--muted)" }}>
                      <div style={{ marginBottom: 10 }}>
                        <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" style={{ opacity: 0.4 }}>
                          <rect x="3" y="3" width="7" height="7" /><rect x="14" y="3" width="7" height="7" /><rect x="14" y="14" width="7" height="7" /><rect x="3" y="14" width="7" height="7" />
                        </svg>
                      </div>
                      <h3 style={{ fontSize: "1rem", fontWeight: 700, color: "var(--ink-light)", marginBottom: 4 }}>
                        {emptyTitle}
                      </h3>
                      <p style={{ fontSize: "0.82rem", maxWidth: 360, margin: "0 auto", lineHeight: 1.5 }}>
                        {emptyDescription}
                      </p>
                    </div>
                  </td>
                </tr>
              )}

              {/* Data rows */}
              {!loading &&
                data.map((row, rowIdx) => {
                  const rowId = String(row[idField] || rowIdx);
                  const isSelected = selectedIds?.has(rowId);
                  return (
                    <tr
                      key={rowId}
                      style={{
                        background: isSelected ? "rgba(0,212,255,0.04)" : "transparent",
                        transition: "background 0.15s",
                      }}
                      onMouseEnter={(e) => {
                        if (!isSelected) (e.currentTarget as HTMLElement).style.background = "rgba(255,255,255,0.02)";
                      }}
                      onMouseLeave={(e) => {
                        (e.currentTarget as HTMLElement).style.background = isSelected ? "rgba(0,212,255,0.04)" : "transparent";
                      }}
                    >
                      {selectable && (
                        <td
                          style={{
                            padding: "12px 10px",
                            borderBottom: "1px solid var(--line)",
                            textAlign: "center",
                          }}
                        >
                          <input
                            type="checkbox"
                            checked={isSelected || false}
                            onChange={(e) => handleSelectRow(rowId, e.target.checked)}
                            style={{ cursor: "pointer", accentColor: "var(--brand)" }}
                          />
                        </td>
                      )}
                      {columns.map((col) => {
                        const cellValue = row[col.key];
                        return (
                          <td
                            key={col.key}
                            style={{
                              padding: "12px 14px",
                              borderBottom: "1px solid var(--line)",
                              fontSize: "0.82rem",
                              color: "var(--ink)",
                              verticalAlign: "middle",
                            }}
                          >
                            {col.render ? col.render(cellValue, row) : (cellValue != null ? String(cellValue) : "-")}
                          </td>
                        );
                      })}
                      {renderActions && (
                        <td
                          style={{
                            padding: "12px 14px",
                            borderBottom: "1px solid var(--line)",
                            textAlign: "right",
                            verticalAlign: "middle",
                          }}
                        >
                          <div style={{ display: "flex", gap: 6, justifyContent: "flex-end", alignItems: "center" }}>
                            {renderActions(row)}
                          </div>
                        </td>
                      )}
                    </tr>
                  );
                })}
            </tbody>
          </table>
        </div>
      </div>

      {/* Pagination */}
      <Pagination
        currentPage={page}
        totalPages={totalPages}
        totalElements={totalElements}
        onPageChange={onPageChange}
        disabled={loading}
      />
    </div>
  );
}
