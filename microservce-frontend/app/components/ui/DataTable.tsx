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
      <div className="glass-card overflow-hidden rounded-[12px] border border-line">
        <div className="overflow-x-auto">
          <table className="w-full border-collapse min-w-[700px]">
            <thead>
              <tr>
                {selectable && (
                  <th className="w-[44px] py-3 px-2.5 sticky top-0 bg-surface-2 border-b border-line text-center z-[2]">
                    <input
                      ref={checkboxAllRef}
                      type="checkbox"
                      checked={allSelected}
                      onChange={(e) => handleSelectAll(e.target.checked)}
                      disabled={loading || data.length === 0}
                      className={loading ? "cursor-not-allowed" : "cursor-pointer"}
                      style={{ accentColor: "var(--brand)" }}
                    />
                  </th>
                )}
                {columns.map((col) => (
                  <th
                    key={col.key}
                    className="py-3 px-3.5 sticky top-0 bg-surface-2 border-b border-line text-left text-xs font-bold text-muted uppercase tracking-wide whitespace-nowrap z-[2]"
                    style={{ width: col.width || "auto" }}
                  >
                    {col.header}
                  </th>
                ))}
                {renderActions && (
                  <th className="py-3 px-3.5 sticky top-0 bg-surface-2 border-b border-line text-right text-xs font-bold text-muted uppercase tracking-wide whitespace-nowrap z-[2]">
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
                      <td className="py-3.5 px-2.5 border-b border-line text-center">
                        <div style={{ ...skeletonBarStyle, width: 16, height: 16, borderRadius: 4, margin: "0 auto" }} />
                      </td>
                    )}
                    {columns.map((col) => (
                      <td key={col.key} className="py-3.5 px-3.5 border-b border-line">
                        <div style={{ ...skeletonBarStyle, width: `${55 + Math.random() * 35}%` }} />
                      </td>
                    ))}
                    {renderActions && (
                      <td className="py-3.5 px-3.5 border-b border-line text-right">
                        <div style={{ ...skeletonBarStyle, width: 60, marginLeft: "auto" }} />
                      </td>
                    )}
                  </tr>
                ))}

              {/* Empty state */}
              {!loading && data.length === 0 && (
                <tr>
                  <td colSpan={totalColumns} className="p-0">
                    <div className="text-center py-[60px] px-6 text-muted">
                      <div className="mb-2.5">
                        <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" className="opacity-40">
                          <rect x="3" y="3" width="7" height="7" /><rect x="14" y="3" width="7" height="7" /><rect x="14" y="14" width="7" height="7" /><rect x="3" y="14" width="7" height="7" />
                        </svg>
                      </div>
                      <h3 className="text-lg font-bold text-ink-light mb-1">
                        {emptyTitle}
                      </h3>
                      <p className="text-sm max-w-[360px] mx-auto leading-relaxed">
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
                      className={`transition-[background] duration-150 ${isSelected ? "bg-[rgba(0,212,255,0.04)]" : "hover:bg-[rgba(255,255,255,0.02)]"}`}
                    >
                      {selectable && (
                        <td className="py-3 px-2.5 border-b border-line text-center">
                          <input
                            type="checkbox"
                            checked={isSelected || false}
                            onChange={(e) => handleSelectRow(rowId, e.target.checked)}
                            className="cursor-pointer"
                            style={{ accentColor: "var(--brand)" }}
                          />
                        </td>
                      )}
                      {columns.map((col) => {
                        const cellValue = row[col.key];
                        return (
                          <td
                            key={col.key}
                            className="py-3 px-3.5 border-b border-line text-sm text-ink align-middle"
                          >
                            {col.render ? col.render(cellValue, row) : (cellValue != null ? String(cellValue) : "-")}
                          </td>
                        );
                      })}
                      {renderActions && (
                        <td className="py-3 px-3.5 border-b border-line text-right align-middle">
                          <div className="flex gap-1.5 justify-end items-center">
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
