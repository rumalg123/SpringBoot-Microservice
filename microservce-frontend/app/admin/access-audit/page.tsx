"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import toast from "react-hot-toast";
import AdminPageShell from "../../components/ui/AdminPageShell";
import { useAuthSession } from "../../../lib/authSession";

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

function getErrorMessage(error: unknown): string {
  if (typeof error === "object" && error !== null) {
    const maybe = error as {
      response?: { data?: { error?: string; message?: string } };
      message?: string;
    };
    return (
      maybe.response?.data?.error ||
      maybe.response?.data?.message ||
      maybe.message ||
      "Request failed"
    );
  }
  return "Request failed";
}

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
        <div
          style={{
            padding: "48px 24px",
            textAlign: "center",
            fontSize: "0.85rem",
            color: "var(--muted)",
          }}
        >
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
        <div
          style={{
            padding: "48px 24px",
            textAlign: "center",
            fontSize: "0.95rem",
            color: "#f87171",
            border: "1px dashed var(--line)",
            borderRadius: 12,
            background: "rgba(248,113,113,0.04)",
          }}
        >
          Unauthorized — Super Admin access required.
        </div>
      </AdminPageShell>
    );
  }

  /* ───── styles ───── */

  const labelStyle: React.CSSProperties = {
    fontSize: "0.7rem",
    fontWeight: 600,
    color: "var(--muted)",
    textTransform: "uppercase",
    letterSpacing: "0.05em",
    marginBottom: 4,
    display: "block",
  };

  const inputStyle: React.CSSProperties = {
    padding: "8px 12px",
    borderRadius: 8,
    border: "1px solid var(--line)",
    background: "var(--surface-2)",
    color: "var(--ink)",
    fontSize: "0.8rem",
    outline: "none",
    width: "100%",
  };

  const selectStyle: React.CSSProperties = {
    ...inputStyle,
    cursor: "pointer",
    appearance: "none",
    backgroundImage:
      "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' fill='rgba(255,255,255,0.5)' viewBox='0 0 16 16'%3E%3Cpath d='M8 11L3 6h10z'/%3E%3C/svg%3E\")",
    backgroundRepeat: "no-repeat",
    backgroundPosition: "right 10px center",
    paddingRight: 28,
  };

  const clearBtnStyle: React.CSSProperties = {
    padding: "8px 16px",
    borderRadius: 8,
    border: "1px solid rgba(248,113,113,0.25)",
    background: "rgba(248,113,113,0.08)",
    color: "#f87171",
    fontSize: "0.78rem",
    fontWeight: 600,
    cursor: "pointer",
    whiteSpace: "nowrap",
    opacity: hasActiveFilters ? 1 : 0.4,
    pointerEvents: hasActiveFilters ? "auto" : "none",
    transition: "opacity 0.15s ease",
  };

  const cardStyle: React.CSSProperties = {
    background: "rgba(255,255,255,0.02)",
    border: "1px solid var(--line)",
    borderRadius: 12,
    padding: 16,
    marginBottom: 8,
  };

  const pillStyle = (bgColor: string, textColor: string): React.CSSProperties => ({
    display: "inline-flex",
    alignItems: "center",
    padding: "3px 10px",
    borderRadius: 999,
    fontSize: "0.68rem",
    fontWeight: 700,
    letterSpacing: "0.03em",
    background: bgColor,
    color: textColor,
    border: `1px solid ${textColor}25`,
    whiteSpace: "nowrap",
  });

  const permPillStyle: React.CSSProperties = {
    display: "inline-flex",
    alignItems: "center",
    padding: "2px 8px",
    borderRadius: 999,
    fontSize: "0.65rem",
    fontWeight: 600,
    border: "1px solid rgba(0,212,255,0.15)",
    color: "rgba(0,212,255,0.85)",
    whiteSpace: "nowrap",
  };

  const metaStyle: React.CSSProperties = {
    fontSize: "0.75rem",
    color: "rgba(255,255,255,0.6)",
  };

  const metaLabelStyle: React.CSSProperties = {
    fontSize: "0.7rem",
    color: "rgba(255,255,255,0.4)",
    marginRight: 4,
  };

  const paginationBtnStyle = (disabled: boolean): React.CSSProperties => ({
    padding: "6px 14px",
    borderRadius: 8,
    border: "1px solid var(--line)",
    background: disabled ? "transparent" : "var(--surface-2)",
    color: disabled ? "rgba(255,255,255,0.3)" : "var(--ink)",
    fontSize: "0.78rem",
    fontWeight: 600,
    cursor: disabled ? "not-allowed" : "pointer",
    opacity: disabled ? 0.5 : 1,
    transition: "opacity 0.15s ease",
  });

  return (
    <AdminPageShell
      title="Access Audit"
      breadcrumbs={[
        { label: "Admin", href: "/admin/orders" },
        { label: "Access Audit" },
      ]}
    >
      {/* ─── Filter Bar ─── */}
      <div
        style={{
          background: "var(--surface-2)",
          border: "1px solid var(--line)",
          borderRadius: 12,
          padding: "14px 16px",
          display: "flex",
          flexWrap: "wrap",
          gap: 12,
          alignItems: "flex-end",
          marginBottom: 20,
        }}
      >
        {/* Target Type */}
        <div style={{ flex: "1 1 160px", minWidth: 140 }}>
          <label style={labelStyle}>Target Type</label>
          <select
            value={targetTypeFilter}
            onChange={(e) => setTargetTypeFilter(e.target.value)}
            style={selectStyle}
          >
            <option value="ALL">All</option>
            <option value="PLATFORM_STAFF">PLATFORM_STAFF</option>
            <option value="VENDOR_STAFF">VENDOR_STAFF</option>
          </select>
        </div>

        {/* Action */}
        <div style={{ flex: "1 1 160px", minWidth: 140 }}>
          <label style={labelStyle}>Action</label>
          <select
            value={actionFilter}
            onChange={(e) => setActionFilter(e.target.value)}
            style={selectStyle}
          >
            <option value="ALL">All</option>
            <option value="CREATED">CREATED</option>
            <option value="UPDATED">UPDATED</option>
            <option value="SOFT_DELETED">SOFT_DELETED</option>
            <option value="RESTORED">RESTORED</option>
          </select>
        </div>

        {/* Actor/Email Search */}
        <div style={{ flex: "2 1 220px", minWidth: 180 }}>
          <label style={labelStyle}>Actor / Email Search</label>
          <div style={{ position: "relative" }}>
            <svg
              width="14"
              height="14"
              viewBox="0 0 24 24"
              fill="none"
              stroke="rgba(255,255,255,0.35)"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              style={{
                position: "absolute",
                left: 10,
                top: "50%",
                transform: "translateY(-50%)",
                pointerEvents: "none",
              }}
            >
              <circle cx="11" cy="11" r="8" />
              <line x1="21" y1="21" x2="16.65" y2="16.65" />
            </svg>
            <input
              type="text"
              placeholder="Search actor, email, reason..."
              value={actorQuery}
              onChange={(e) => setActorQuery(e.target.value)}
              style={{ ...inputStyle, paddingLeft: 32 }}
            />
          </div>
        </div>

        {/* Date From */}
        <div style={{ flex: "1 1 150px", minWidth: 130 }}>
          <label style={labelStyle}>Date From</label>
          <input
            type="date"
            value={fromDate}
            onChange={(e) => setFromDate(e.target.value)}
            style={inputStyle}
          />
        </div>

        {/* Date To */}
        <div style={{ flex: "1 1 150px", minWidth: 130 }}>
          <label style={labelStyle}>Date To</label>
          <input
            type="date"
            value={toDate}
            onChange={(e) => setToDate(e.target.value)}
            style={inputStyle}
          />
        </div>

        {/* Clear All */}
        <div style={{ flex: "0 0 auto" }}>
          <button
            type="button"
            style={clearBtnStyle}
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
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          marginBottom: 12,
          padding: "0 2px",
        }}
      >
        <span style={{ fontSize: "0.78rem", color: "var(--muted)" }}>
          {loading
            ? "Loading..."
            : `${totalElements} record${totalElements !== 1 ? "s" : ""} found`}
        </span>
        <button
          type="button"
          onClick={() => void load()}
          disabled={loading}
          style={{
            padding: "5px 12px",
            borderRadius: 8,
            border: "1px solid var(--line)",
            background: "var(--surface-2)",
            color: "var(--ink)",
            fontSize: "0.75rem",
            fontWeight: 600,
            cursor: loading ? "not-allowed" : "pointer",
            opacity: loading ? 0.5 : 1,
          }}
        >
          {loading ? "Refreshing..." : "Refresh"}
        </button>
      </div>

      {/* ─── Rows ─── */}
      {loading && rows.length === 0 ? (
        <div
          style={{
            padding: "48px 24px",
            textAlign: "center",
            fontSize: "0.85rem",
            color: "var(--muted)",
            border: "1px dashed var(--line)",
            borderRadius: 12,
          }}
        >
          Loading audit records...
        </div>
      ) : rows.length === 0 ? (
        <div
          style={{
            padding: "48px 24px",
            textAlign: "center",
            fontSize: "0.85rem",
            color: "var(--muted)",
            border: "1px dashed var(--line)",
            borderRadius: 12,
          }}
        >
          No audit records found.
        </div>
      ) : (
        <div>
          {rows.map((row) => (
            <div key={row.id} style={cardStyle}>
              {/* Top row: action badge, timestamp, status indicators */}
              <div
                style={{
                  display: "flex",
                  flexWrap: "wrap",
                  alignItems: "center",
                  justifyContent: "space-between",
                  gap: 8,
                  marginBottom: 10,
                }}
              >
                <div style={{ display: "flex", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
                  <span
                    style={pillStyle(
                      actionBadgeBg(row.action),
                      actionBadgeColor(row.action)
                    )}
                  >
                    {row.action}
                  </span>
                  <span style={{ fontSize: "0.75rem", color: "rgba(255,255,255,0.5)" }}>
                    {formatDateTime(row.createdAt)}
                  </span>
                </div>
                <div style={{ display: "flex", gap: 10, alignItems: "center" }}>
                  <span
                    style={{
                      fontSize: "0.7rem",
                      fontWeight: 600,
                      color: row.activeAfter ? "#86efac" : "#fca5a5",
                    }}
                  >
                    {row.activeAfter ? "Active" : "Inactive"}
                  </span>
                  <span
                    style={{
                      fontSize: "0.7rem",
                      fontWeight: 600,
                      color: row.deletedAfter ? "#fde68a" : "rgba(255,255,255,0.4)",
                    }}
                  >
                    {row.deletedAfter ? "Deleted" : "Not Deleted"}
                  </span>
                </div>
              </div>

              {/* Second row: target type, email, actor info */}
              <div
                style={{
                  display: "flex",
                  flexWrap: "wrap",
                  gap: "6px 16px",
                  marginBottom: row.permissions && row.permissions.length > 0 ? 10 : 0,
                }}
              >
                <span style={metaStyle}>
                  <span style={metaLabelStyle}>Type:</span>
                  {row.targetType}
                </span>
                {row.email && (
                  <span style={metaStyle}>
                    <span style={metaLabelStyle}>Email:</span>
                    {row.email}
                  </span>
                )}
                <span style={metaStyle}>
                  <span style={metaLabelStyle}>Actor:</span>
                  {row.actorSub || "system"}
                  {row.actorType ? ` (${row.actorType})` : ""}
                </span>
                {row.changeSource && (
                  <span style={metaStyle}>
                    <span style={metaLabelStyle}>Source:</span>
                    {row.changeSource}
                  </span>
                )}
                {row.vendorId && (
                  <span style={metaStyle}>
                    <span style={metaLabelStyle}>Vendor:</span>
                    <span style={{ fontFamily: "monospace", fontSize: "0.7rem" }}>
                      {row.vendorId}
                    </span>
                  </span>
                )}
                {row.reason && (
                  <span style={{ ...metaStyle, color: "rgba(255,255,255,0.75)" }}>
                    <span style={metaLabelStyle}>Reason:</span>
                    {row.reason}
                  </span>
                )}
              </div>

              {/* Third row: permissions */}
              {row.permissions && row.permissions.length > 0 && (
                <div style={{ display: "flex", flexWrap: "wrap", gap: 6 }}>
                  {row.permissions.map((perm) => (
                    <span key={`${row.id}:${perm}`} style={permPillStyle}>
                      {perm}
                    </span>
                  ))}
                </div>
              )}
            </div>
          ))}

          {/* ─── Pagination ─── */}
          <div
            style={{
              display: "flex",
              flexWrap: "wrap",
              alignItems: "center",
              justifyContent: "space-between",
              gap: 12,
              marginTop: 16,
              padding: "12px 16px",
              background: "rgba(255,255,255,0.02)",
              border: "1px solid var(--line)",
              borderRadius: 12,
            }}
          >
            {/* Page size selector */}
            <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
              <span style={{ fontSize: "0.75rem", color: "var(--muted)" }}>
                Page Size
              </span>
              <select
                value={pageSize}
                onChange={(e) => {
                  setPageSize(Number(e.target.value));
                  setPage(0);
                }}
                style={{
                  ...selectStyle,
                  width: "auto",
                  padding: "4px 28px 4px 10px",
                }}
              >
                <option value={10}>10</option>
                <option value={20}>20</option>
                <option value={50}>50</option>
              </select>
            </div>

            {/* Page info and navigation */}
            <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
              <button
                type="button"
                disabled={!hasPrevPage || loading}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                style={paginationBtnStyle(!hasPrevPage || loading)}
              >
                Prev
              </button>
              <span style={{ fontSize: "0.78rem", color: "var(--muted)", whiteSpace: "nowrap" }}>
                {totalPages === 0
                  ? "No pages"
                  : `Page ${page + 1} of ${totalPages}`}
              </span>
              <button
                type="button"
                disabled={!hasNextPage || loading}
                onClick={() => setPage((p) => p + 1)}
                style={paginationBtnStyle(!hasNextPage || loading)}
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
