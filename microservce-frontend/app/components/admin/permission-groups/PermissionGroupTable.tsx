"use client";

import StatusBadge from "../../ui/StatusBadge";
import { formatDate } from "../../../../lib/format";

type PermissionGroup = {
  id: string;
  name: string;
  description: string | null;
  permissions: string[];
  scope: "PLATFORM" | "VENDOR";
  createdAt: string;
};

type ScopeFilter = "ALL" | "PLATFORM" | "VENDOR";

const SCOPE_COLORS: Record<string, { bg: string; border: string; color: string }> = {
  PLATFORM: { bg: "var(--brand-soft)", border: "var(--line-bright)", color: "var(--brand)" },
  VENDOR: { bg: "var(--accent-soft)", border: "rgba(124,58,237,0.3)", color: "var(--accent)" },
};

type PermissionGroupTableProps = {
  groups: PermissionGroup[];
  loading: boolean;
  totalPages: number;
  totalElements: number;
  page: number;
  scopeFilter: ScopeFilter;
  onScopeFilterChange: (value: ScopeFilter) => void;
  onEdit: (group: PermissionGroup) => void;
  onDelete: (group: PermissionGroup) => void;
  onPageChange: (page: number) => void;
};

export default function PermissionGroupTable({
  groups,
  loading,
  totalPages,
  totalElements,
  page,
  scopeFilter,
  onScopeFilterChange,
  onEdit,
  onDelete,
  onPageChange,
}: PermissionGroupTableProps) {
  const scopeOptions: { label: string; value: ScopeFilter }[] = [
    { label: "All", value: "ALL" },
    { label: "Platform", value: "PLATFORM" },
    { label: "Vendor", value: "VENDOR" },
  ];

  return (
    <>
      {/* Scope Filter Tabs */}
      <div className="flex gap-1.5 mb-[18px] flex-wrap">
        {scopeOptions.map((opt) => {
          const active = scopeFilter === opt.value;
          return (
            <button
              key={opt.value}
              type="button"
              onClick={() => onScopeFilterChange(opt.value)}
              className={`py-1.5 px-4 rounded-md text-[0.78rem] font-bold cursor-pointer transition-all duration-150 ${active ? "border border-brand bg-brand-soft text-brand" : "border border-line bg-transparent text-muted"}`}
            >
              {opt.label}
            </button>
          );
        })}
        <span className="ml-auto flex items-center text-[0.75rem] text-muted">
          {totalElements} group{totalElements !== 1 ? "s" : ""}
        </span>
      </div>

      {/* Table */}
      <div className="bg-[rgba(255,255,255,0.03)] border border-line rounded-lg overflow-auto">
        <table className="w-full border-collapse">
          <thead>
            <tr>
              {["Name", "Scope", "Permissions", "Created", "Actions"].map((h) => (
                <th
                  key={h}
                  className="bg-surface-2 text-muted text-[0.72rem] uppercase tracking-[0.05em] py-3 px-3.5 text-left font-bold whitespace-nowrap"
                >
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td
                  colSpan={5}
                  className="py-10 px-3.5 text-center text-[0.82rem] text-muted"
                >
                  Loading...
                </td>
              </tr>
            ) : groups.length === 0 ? (
              <tr>
                <td
                  colSpan={5}
                  className="py-10 px-3.5 text-center text-[0.82rem] text-muted"
                >
                  No permission groups found.
                </td>
              </tr>
            ) : (
              groups.map((g) => (
                <tr key={g.id}>
                  {/* Name */}
                  <td className="text-[0.82rem] text-ink py-3 px-3.5 border-b border-line font-semibold">
                    <div>{g.name}</div>
                    {g.description && (
                      <div className="text-[0.72rem] text-muted mt-0.5 max-w-[260px] overflow-hidden text-ellipsis whitespace-nowrap">
                        {g.description}
                      </div>
                    )}
                  </td>

                  {/* Scope */}
                  <td className="text-[0.82rem] text-ink py-3 px-3.5 border-b border-line">
                    <StatusBadge value={g.scope} colorMap={SCOPE_COLORS} />
                  </td>

                  {/* Permissions count */}
                  <td className="text-[0.82rem] text-ink py-3 px-3.5 border-b border-line">
                    <span className="inline-block py-0.5 px-2.5 rounded-full text-[0.72rem] font-bold bg-[rgba(255,255,255,0.06)] border border-line text-ink">
                      {g.permissions.length}
                    </span>
                  </td>

                  {/* Created */}
                  <td className="text-[0.82rem] text-ink py-3 px-3.5 border-b border-line whitespace-nowrap">
                    {formatDate(g.createdAt)}
                  </td>

                  {/* Actions */}
                  <td className="text-[0.82rem] text-ink py-3 px-3.5 border-b border-line whitespace-nowrap">
                    <div className="flex gap-1.5">
                      <button
                        type="button"
                        className="btn-ghost text-[0.78rem] py-1 px-3 rounded-[8px] cursor-pointer"
                        onClick={() => onEdit(g)}
                      >
                        Edit
                      </button>
                      <button
                        type="button"
                        className="btn-ghost text-[0.78rem] py-1 px-3 rounded-[8px] text-[#f87171] cursor-pointer"
                        onClick={() => onDelete(g)}
                      >
                        Delete
                      </button>
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-center gap-3 mt-5">
          <button
            type="button"
            onClick={() => onPageChange(page - 1)}
            disabled={page <= 0 || loading}
            className={`py-[7px] px-4 rounded-md text-[0.78rem] font-semibold border border-line transition-opacity duration-150 ${page <= 0 ? "bg-transparent text-muted cursor-not-allowed opacity-50" : "bg-[rgba(255,255,255,0.04)] text-ink cursor-pointer opacity-100"}`}
          >
            Prev
          </button>
          <span className="text-[0.78rem] text-muted">
            Page {page + 1} of {totalPages}
          </span>
          <button
            type="button"
            onClick={() => onPageChange(page + 1)}
            disabled={page >= totalPages - 1 || loading}
            className={`py-[7px] px-4 rounded-md text-[0.78rem] font-semibold border border-line transition-opacity duration-150 ${page >= totalPages - 1 ? "bg-transparent text-muted cursor-not-allowed opacity-50" : "bg-[rgba(255,255,255,0.04)] text-ink cursor-pointer opacity-100"}`}
          >
            Next
          </button>
        </div>
      )}
    </>
  );
}
