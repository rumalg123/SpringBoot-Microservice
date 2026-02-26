"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import toast from "react-hot-toast";
import AdminPageShell from "../../components/ui/AdminPageShell";
import { useAuthSession } from "../../../lib/authSession";
import { getErrorMessage } from "../../../lib/error";

/* ───── types ───── */

type AccessAuditRow = {
  id: string;
  targetType: string;
  targetId: string;
  vendorId?: string | null;
  keycloakUserId?: string | null;
  email?: string | null;
  action: string;
  activeAfter: boolean;
  deletedAfter: boolean;
  permissions?: string[];
  actorSub?: string | null;
  actorRoles?: string | null;
  actorType?: string | null;
  changeSource?: string | null;
  reason?: string | null;
  createdAt?: string | null;
};

type PageResponse = {
  items: AccessAuditRow[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

/* ───── helpers ───── */


function formatDateTime(value?: string | null): string {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString();
}

const ACTION_BADGE_COLORS: Record<string, string> = {
  CREATED: "#34d399",
  UPDATED: "var(--brand)",
  SOFT_DELETED: "#f87171",
  RESTORED: "#fbbf24",
};

function actionBadgeBg(action: string): string {
  const color = ACTION_BADGE_COLORS[action];
  if (!color) return "rgba(255,255,255,0.08)";
  if (color.startsWith("var(")) return "rgba(0,212,255,0.12)";
  // hex to rgba
  const hex = color.replace("#", "");
  const r = parseInt(hex.substring(0, 2), 16);
  const g = parseInt(hex.substring(2, 4), 16);
  const b = parseInt(hex.substring(4, 6), 16);
  return `rgba(${r},${g},${b},0.12)`;
}

function actionBadgeColor(action: string): string {
  return ACTION_BADGE_COLORS[action] || "rgba(255,255,255,0.7)";
}

/* ───── component ───── */

export default function AccessAuditPage() {
  const session = useAuthSession();

  /* data state */
  const [rows, setRows] = useState<AccessAuditRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  /* filter state */
  const [targetTypeFilter, setTargetTypeFilter] = useState("ALL");
  const [actionFilter, setActionFilter] = useState("ALL");
  const [actorQuery, setActorQuery] = useState("");
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");

  /* debounce ref */
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [debouncedActorQuery, setDebouncedActorQuery] = useState("");

  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => {
      setDebouncedActorQuery(actorQuery);
    }, 400);
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, [actorQuery]);

  /* reset page when filters change */
  useEffect(() => {
    setPage(0);
  }, [targetTypeFilter, actionFilter, debouncedActorQuery, fromDate, toDate]);

  /* build query string */
  const queryString = useMemo(() => {
    const params = new URLSearchParams();
    if (targetTypeFilter !== "ALL") params.set("targetType", targetTypeFilter);
    if (actionFilter !== "ALL") params.set("action", actionFilter);
    if (debouncedActorQuery.trim()) params.set("actorQuery", debouncedActorQuery.trim());
    if (fromDate) {
      params.set("from", new Date(`${fromDate}T00:00:00.000Z`).toISOString());
    }
    if (toDate) {
      params.set("to", new Date(`${toDate}T23:59:59.999Z`).toISOString());
    }
    params.set("page", String(page));
    params.set("size", String(pageSize));
    return params.toString();
  }, [targetTypeFilter, actionFilter, debouncedActorQuery, fromDate, toDate, page, pageSize]);

  /* fetch */
  const load = useCallback(async () => {
    if (!session.apiClient) return;
    setLoading(true);
    try {
      const res = await session.apiClient.get(`/admin/access-audit?${queryString}`);
      const data = res.data as PageResponse | AccessAuditRow[];
      if (Array.isArray(data)) {
        setRows(data.filter(Boolean));
        setTotalElements(data.length);
        setTotalPages(data.length ? 1 : 0);
      } else {
        setRows((data.items || []).filter(Boolean));
        setTotalElements(Number(data.totalElements || 0));
        setTotalPages(Number(data.totalPages || 0));
      }
    } catch (error) {
      toast.error(getErrorMessage(error));
    } finally {
      setLoading(false);
    }
  }, [session.apiClient, queryString]);

  useEffect(() => {
    if (session.status !== "ready") return;
    if (!session.isSuperAdmin) return;
    void load();
  }, [session.status, session.isSuperAdmin, load]);

  /* derived */
  const hasActiveFilters =
    targetTypeFilter !== "ALL" ||
    actionFilter !== "ALL" ||
    actorQuery.trim() !== "" ||
    fromDate !== "" ||
    toDate !== "";

  const hasPrevPage = page > 0;
  const hasNextPage = totalPages > 0 ? page + 1 < totalPages : rows.length >= pageSize;

  /* guard */
  if (session.status === "loading" || session.status === "idle") {
    return (
      <AdminPageShell
        title="Access Audit"
        breadcrumbs={[
          { label: "Admin", href: "/admin/orders" },
          { label: "Access Audit" },
        ]}
      >
        <div className="py-12 px-6 text-center text-base text-muted">
          Loading...
        </div>
      </AdminPageShell>
    );
  }

  if (!session.isSuperAdmin) {
    return (
      <AdminPageShell
        title="Access Audit"
        breadcrumbs={[
          { label: "Admin", href: "/admin/orders" },
          { label: "Access Audit" },
        ]}
      >
        <div className="py-12 px-6 text-center text-base text-[#f87171] border border-dashed border-line rounded-[12px] bg-[rgba(248,113,113,0.04)]">
          Unauthorized — Super Admin access required.
        </div>
      </AdminPageShell>
    );
  }

  return (
    <AdminPageShell
      title="Access Audit"
      breadcrumbs={[
        { label: "Admin", href: "/admin/orders" },
        { label: "Access Audit" },
      ]}
    >
      {/* ─── Filter Bar ─── */}
      <div className="bg-surface-2 border border-line rounded-[12px] py-3.5 px-4 flex flex-wrap gap-3 items-end mb-5">
        {/* Target Type */}
        <div className="flex-[1_1_160px] min-w-[140px]">
          <label className="block text-xs font-semibold text-muted uppercase tracking-[0.05em] mb-1">Target Type</label>
          <select
            value={targetTypeFilter}
            onChange={(e) => setTargetTypeFilter(e.target.value)}
            className="filter-select w-full"
          >
            <option value="ALL">All</option>
            <option value="PLATFORM_STAFF">PLATFORM_STAFF</option>
            <option value="VENDOR_STAFF">VENDOR_STAFF</option>
          </select>
        </div>

        {/* Action */}
        <div className="flex-[1_1_160px] min-w-[140px]">
          <label className="block text-xs font-semibold text-muted uppercase tracking-[0.05em] mb-1">Action</label>
          <select
            value={actionFilter}
            onChange={(e) => setActionFilter(e.target.value)}
            className="filter-select w-full"
          >
            <option value="ALL">All</option>
            <option value="CREATED">CREATED</option>
            <option value="UPDATED">UPDATED</option>
            <option value="SOFT_DELETED">SOFT_DELETED</option>
            <option value="RESTORED">RESTORED</option>
          </select>
        </div>

        {/* Actor/Email Search */}
        <div className="flex-[2_1_220px] min-w-[180px]">
          <label className="block text-xs font-semibold text-muted uppercase tracking-[0.05em] mb-1">Actor / Email Search</label>
          <div className="relative">
            <svg
              width="14"
              height="14"
              viewBox="0 0 24 24"
              fill="none"
              stroke="rgba(255,255,255,0.35)"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              className="absolute left-2.5 top-1/2 -translate-y-1/2 pointer-events-none"
            >
              <circle cx="11" cy="11" r="8" />
              <line x1="21" y1="21" x2="16.65" y2="16.65" />
            </svg>
            <input
              type="text"
              placeholder="Search actor, email, reason..."
              value={actorQuery}
              onChange={(e) => setActorQuery(e.target.value)}
              className="w-full py-2 pr-3 pl-8 rounded-[8px] border border-line bg-surface-2 text-ink text-sm outline-none"
            />
          </div>
        </div>

        {/* Date From */}
        <div className="flex-[1_1_150px] min-w-[130px]">
          <label className="block text-xs font-semibold text-muted uppercase tracking-[0.05em] mb-1">Date From</label>
          <input
            type="date"
            value={fromDate}
            onChange={(e) => setFromDate(e.target.value)}
            className="w-full py-2 px-3 rounded-[8px] border border-line bg-surface-2 text-ink text-sm outline-none"
          />
        </div>

        {/* Date To */}
        <div className="flex-[1_1_150px] min-w-[130px]">
          <label className="block text-xs font-semibold text-muted uppercase tracking-[0.05em] mb-1">Date To</label>
          <input
            type="date"
            value={toDate}
            onChange={(e) => setToDate(e.target.value)}
            className="w-full py-2 px-3 rounded-[8px] border border-line bg-surface-2 text-ink text-sm outline-none"
          />
        </div>

        {/* Clear All */}
        <div className="flex-[0_0_auto]">
          <button
            type="button"
            className={`py-2 px-4 rounded-[8px] border border-[rgba(248,113,113,0.25)] bg-[rgba(248,113,113,0.08)] text-[#f87171] text-[0.78rem] font-semibold cursor-pointer whitespace-nowrap transition-opacity duration-150 ${hasActiveFilters ? "opacity-100" : "opacity-40 pointer-events-none"}`}
            onClick={() => {
              setTargetTypeFilter("ALL");
              setActionFilter("ALL");
              setActorQuery("");
              setFromDate("");
              setToDate("");
            }}
          >
            Clear All
          </button>
        </div>
      </div>

      {/* ─── Summary bar ─── */}
      <div className="flex justify-between items-center mb-3 px-0.5">
        <span className="text-[0.78rem] text-muted">
          {loading
            ? "Loading..."
            : `${totalElements} record${totalElements !== 1 ? "s" : ""} found`}
        </span>
        <button
          type="button"
          onClick={() => void load()}
          disabled={loading}
          className={`py-1.5 px-3 rounded-[8px] border border-line bg-surface-2 text-ink text-[0.75rem] font-semibold ${loading ? "cursor-not-allowed opacity-50" : "cursor-pointer opacity-100"}`}
        >
          {loading ? "Refreshing..." : "Refresh"}
        </button>
      </div>

      {/* ─── Rows ─── */}
      {loading && rows.length === 0 ? (
        <div className="py-12 px-6 text-center text-base text-muted border border-dashed border-line rounded-[12px]">
          Loading audit records...
        </div>
      ) : rows.length === 0 ? (
        <div className="py-12 px-6 text-center text-base text-muted border border-dashed border-line rounded-[12px]">
          No audit records found.
        </div>
      ) : (
        <div>
          {rows.map((row) => (
            <div key={row.id} className="bg-[rgba(255,255,255,0.02)] border border-line rounded-[12px] p-4 mb-2">
              {/* Top row: action badge, timestamp, status indicators */}
              <div className="flex flex-wrap items-center justify-between gap-2 mb-2.5">
                <div className="flex items-center gap-2 flex-wrap">
                  <span
                    className="inline-flex items-center py-[3px] px-2.5 rounded-full text-[0.68rem] font-bold tracking-[0.03em] whitespace-nowrap"
                    style={{
                      background: actionBadgeBg(row.action),
                      color: actionBadgeColor(row.action),
                      border: `1px solid ${actionBadgeColor(row.action)}25`,
                    }}
                  >
                    {row.action}
                  </span>
                  <span className="text-[0.75rem] text-[rgba(255,255,255,0.5)]">
                    {formatDateTime(row.createdAt)}
                  </span>
                </div>
                <div className="flex gap-2.5 items-center">
                  <span
                    className="text-xs font-semibold"
                    style={{ color: row.activeAfter ? "#86efac" : "#fca5a5" }}
                  >
                    {row.activeAfter ? "Active" : "Inactive"}
                  </span>
                  <span
                    className="text-xs font-semibold"
                    style={{ color: row.deletedAfter ? "#fde68a" : "rgba(255,255,255,0.4)" }}
                  >
                    {row.deletedAfter ? "Deleted" : "Not Deleted"}
                  </span>
                </div>
              </div>

              {/* Second row: target type, email, actor info */}
              <div
                className="flex flex-wrap gap-x-4 gap-y-1.5"
                style={{ marginBottom: row.permissions && row.permissions.length > 0 ? 10 : 0 }}
              >
                <span className="text-[0.75rem] text-[rgba(255,255,255,0.6)]">
                  <span className="text-xs text-[rgba(255,255,255,0.4)] mr-1">Type:</span>
                  {row.targetType}
                </span>
                {row.email && (
                  <span className="text-[0.75rem] text-[rgba(255,255,255,0.6)]">
                    <span className="text-xs text-[rgba(255,255,255,0.4)] mr-1">Email:</span>
                    {row.email}
                  </span>
                )}
                <span className="text-[0.75rem] text-[rgba(255,255,255,0.6)]">
                  <span className="text-xs text-[rgba(255,255,255,0.4)] mr-1">Actor:</span>
                  {row.actorSub || "system"}
                  {row.actorType ? ` (${row.actorType})` : ""}
                </span>
                {row.changeSource && (
                  <span className="text-[0.75rem] text-[rgba(255,255,255,0.6)]">
                    <span className="text-xs text-[rgba(255,255,255,0.4)] mr-1">Source:</span>
                    {row.changeSource}
                  </span>
                )}
                {row.vendorId && (
                  <span className="text-[0.75rem] text-[rgba(255,255,255,0.6)]">
                    <span className="text-xs text-[rgba(255,255,255,0.4)] mr-1">Vendor:</span>
                    <span className="font-mono text-xs">
                      {row.vendorId}
                    </span>
                  </span>
                )}
                {row.reason && (
                  <span className="text-[0.75rem] text-[rgba(255,255,255,0.75)]">
                    <span className="text-xs text-[rgba(255,255,255,0.4)] mr-1">Reason:</span>
                    {row.reason}
                  </span>
                )}
              </div>

              {/* Third row: permissions */}
              {row.permissions && row.permissions.length > 0 && (
                <div className="flex flex-wrap gap-1.5">
                  {row.permissions.map((perm) => (
                    <span key={`${row.id}:${perm}`} className="inline-flex items-center py-0.5 px-2 rounded-full text-[0.65rem] font-semibold border border-[rgba(0,212,255,0.15)] text-[rgba(0,212,255,0.85)] whitespace-nowrap">
                      {perm}
                    </span>
                  ))}
                </div>
              )}
            </div>
          ))}

          {/* ─── Pagination ─── */}
          <div className="flex flex-wrap items-center justify-between gap-3 mt-4 py-3 px-4 bg-[rgba(255,255,255,0.02)] border border-line rounded-[12px]">
            {/* Page size selector */}
            <div className="flex items-center gap-2">
              <span className="text-[0.75rem] text-muted">
                Page Size
              </span>
              <select
                value={pageSize}
                onChange={(e) => {
                  setPageSize(Number(e.target.value));
                  setPage(0);
                }}
                className="filter-select"
                style={{ minWidth: "auto", padding: "4px 28px 4px 10px" }}
              >
                <option value={10}>10</option>
                <option value={20}>20</option>
                <option value={50}>50</option>
              </select>
            </div>

            {/* Page info and navigation */}
            <div className="flex items-center gap-2">
              <button
                type="button"
                disabled={!hasPrevPage || loading}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                className={`py-1.5 px-3.5 rounded-[8px] border border-line text-[0.78rem] font-semibold transition-opacity duration-150 ${!hasPrevPage || loading ? "bg-transparent text-[rgba(255,255,255,0.3)] cursor-not-allowed opacity-50" : "bg-surface-2 text-ink cursor-pointer opacity-100"}`}
              >
                Prev
              </button>
              <span className="text-[0.78rem] text-muted whitespace-nowrap">
                {totalPages === 0
                  ? "No pages"
                  : `Page ${page + 1} of ${totalPages}`}
              </span>
              <button
                type="button"
                disabled={!hasNextPage || loading}
                onClick={() => setPage((p) => p + 1)}
                className={`py-1.5 px-3.5 rounded-[8px] border border-line text-[0.78rem] font-semibold transition-opacity duration-150 ${!hasNextPage || loading ? "bg-transparent text-[rgba(255,255,255,0.3)] cursor-not-allowed opacity-50" : "bg-surface-2 text-ink cursor-pointer opacity-100"}`}
              >
                Next
              </button>
            </div>
          </div>
        </div>
      )}
    </AdminPageShell>
  );
}
