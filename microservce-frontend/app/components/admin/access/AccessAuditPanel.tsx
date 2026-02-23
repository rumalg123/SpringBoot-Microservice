"use client";

import type { AxiosInstance } from "axios";
import { useCallback, useEffect, useMemo, useState } from "react";
import toast from "react-hot-toast";

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

type Props = {
  apiClient: AxiosInstance | null;
  title?: string;
  targetType: "PLATFORM_STAFF" | "VENDOR_STAFF";
  targetId: string | null;
  vendorId?: string | null;
  limit?: number;
  reloadKey?: number;
};

type AccessAuditPageResponse = {
  items?: AccessAuditRow[];
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
};

function getErrorMessage(error: unknown): string {
  if (typeof error === "object" && error !== null) {
    const maybe = error as { response?: { data?: { error?: string; message?: string } }; message?: string };
    return maybe.response?.data?.error || maybe.response?.data?.message || maybe.message || "Request failed";
  }
  return "Request failed";
}

function formatDateTime(value?: string | null): string {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString();
}

export default function AccessAuditPanel({
  apiClient,
  title = "Access Audit",
  targetType,
  targetId,
  vendorId,
  limit = 20,
  reloadKey = 0,
}: Props) {
  const [rows, setRows] = useState<AccessAuditRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(limit);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [actionFilter, setActionFilter] = useState("ALL");
  const [actorQuery, setActorQuery] = useState("");
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");

  const canLoad = Boolean(apiClient && targetId);

  const query = useMemo(() => {
    if (!targetId) return "";
    const params = new URLSearchParams();
    params.set("targetType", targetType);
    params.set("targetId", targetId);
    if (vendorId) params.set("vendorId", vendorId);
    if (actionFilter !== "ALL") params.set("action", actionFilter);
    if (actorQuery.trim()) params.set("actorQuery", actorQuery.trim());
    if (fromDate) {
      const fromIso = new Date(`${fromDate}T00:00:00.000Z`).toISOString();
      params.set("from", fromIso);
    }
    if (toDate) {
      const toIso = new Date(`${toDate}T23:59:59.999Z`).toISOString();
      params.set("to", toIso);
    }
    params.set("page", String(page));
    params.set("size", String(pageSize));
    return params.toString();
  }, [targetType, targetId, vendorId, page, pageSize, actionFilter, actorQuery, fromDate, toDate]);

  const load = useCallback(async () => {
    if (!apiClient || !targetId || !query) return;
    setLoading(true);
    try {
      const res = await apiClient.get(`/admin/access-audit?${query}`);
      const data = res.data as AccessAuditPageResponse | AccessAuditRow[];
      if (Array.isArray(data)) {
        setRows(data.filter(Boolean));
        setTotalElements(data.length);
        setTotalPages(data.length ? 1 : 0);
        return;
      }
      const items = (data.items || []).filter(Boolean);
      setRows(items);
      setTotalElements(Number(data.totalElements || 0));
      setTotalPages(Number(data.totalPages || 0));
      if (typeof data.page === "number" && data.page !== page) {
        setPage(data.page);
      }
      if (typeof data.size === "number" && data.size !== pageSize) {
        setPageSize(data.size);
      }
    } catch (error) {
      toast.error(getErrorMessage(error));
    } finally {
      setLoading(false);
    }
  }, [apiClient, targetId, query, page, pageSize]);

  useEffect(() => {
    if (!canLoad) {
      setRows([]);
      return;
    }
    void load();
  }, [canLoad, load, reloadKey]);

  useEffect(() => {
    setPage(0);
  }, [targetId, vendorId, actionFilter, actorQuery, fromDate, toDate]);

  const actionOptions = useMemo(() => {
    const unique = new Set<string>();
    rows.forEach((row) => {
      if (row.action) unique.add(String(row.action).trim());
    });
    return ["ALL", ...Array.from(unique).sort()];
  }, [rows]);

  const hasActiveFilters = actionFilter !== "ALL" || actorQuery.trim() !== "" || fromDate !== "" || toDate !== "";
  const hasPrevPage = page > 0;
  const hasNextPage = totalPages > 0 ? page + 1 < totalPages : rows.length >= pageSize;

  return (
    <section className="space-y-3 rounded-2xl p-4" style={{ background: "rgba(255,255,255,0.02)", border: "1px solid var(--line)" }}>
      <div className="flex items-center justify-between gap-3">
        <div>
          <h3 className="text-sm font-semibold" style={{ color: "var(--ink)" }}>{title}</h3>
          <p className="text-xs" style={{ color: "rgba(255,255,255,0.6)" }}>
            {targetId ? `Showing latest access changes for ${targetType.toLowerCase().replace("_", " ")}.` : "Select a row to view audit history."}
          </p>
        </div>
        <button className="btn-ghost" onClick={() => { void load(); }} disabled={!canLoad || loading}>
          {loading ? "Refreshing..." : "Refresh"}
        </button>
      </div>

      {targetId && (
        <div className="grid gap-2 rounded-xl p-3" style={{ border: "1px solid rgba(255,255,255,0.08)", background: "rgba(255,255,255,0.015)" }}>
          <div className="grid gap-2 md:grid-cols-4">
            <label className="space-y-1 text-xs">
              <span style={{ color: "rgba(255,255,255,0.6)" }}>Action</span>
              <select
                value={actionFilter}
                onChange={(e) => setActionFilter(e.target.value)}
                className="w-full rounded-lg px-2 py-2"
                style={{ background: "var(--surface-2)", border: "1px solid var(--line)", color: "var(--ink)" }}
              >
                {actionOptions.map((option) => (
                  <option key={option} value={option}>{option}</option>
                ))}
              </select>
            </label>

            <label className="space-y-1 text-xs md:col-span-2">
              <span style={{ color: "rgba(255,255,255,0.6)" }}>Actor / Reason / Email</span>
              <input
                value={actorQuery}
                onChange={(e) => setActorQuery(e.target.value)}
                placeholder="Search actor, reason, email..."
                className="w-full rounded-lg px-2 py-2"
                style={{ background: "rgba(255,255,255,0.03)", border: "1px solid var(--line)", color: "var(--ink)" }}
              />
            </label>

            <div className="flex items-end">
              <button
                className="btn-ghost w-full"
                disabled={!hasActiveFilters}
                onClick={() => {
                  setActionFilter("ALL");
                  setActorQuery("");
                  setFromDate("");
                  setToDate("");
                  setPage(0);
                }}
              >
                Clear Filters
              </button>
            </div>
          </div>

          <div className="grid gap-2 md:grid-cols-3">
            <label className="space-y-1 text-xs">
              <span style={{ color: "rgba(255,255,255,0.6)" }}>From Date</span>
              <input
                type="date"
                value={fromDate}
                onChange={(e) => setFromDate(e.target.value)}
                className="w-full rounded-lg px-2 py-2"
                style={{ background: "rgba(255,255,255,0.03)", border: "1px solid var(--line)", color: "var(--ink)" }}
              />
            </label>
            <label className="space-y-1 text-xs">
              <span style={{ color: "rgba(255,255,255,0.6)" }}>To Date</span>
              <input
                type="date"
                value={toDate}
                onChange={(e) => setToDate(e.target.value)}
                className="w-full rounded-lg px-2 py-2"
                style={{ background: "rgba(255,255,255,0.03)", border: "1px solid var(--line)", color: "var(--ink)" }}
              />
            </label>
            <div className="flex items-end">
              <div className="w-full rounded-lg px-3 py-2 text-xs" style={{ border: "1px solid rgba(255,255,255,0.08)", color: "rgba(255,255,255,0.7)" }}>
                Page {totalPages === 0 ? 0 : page + 1} / {Math.max(totalPages, 1)} • {rows.length} rows • {totalElements} total
              </div>
            </div>
          </div>
        </div>
      )}

      {!targetId ? (
        <div className="rounded-xl px-4 py-5 text-sm" style={{ border: "1px dashed var(--line)", color: "rgba(255,255,255,0.65)" }}>
          Select a staff access row and click `History` to inspect changes.
        </div>
      ) : loading && rows.length === 0 ? (
        <div className="rounded-xl px-4 py-5 text-sm" style={{ border: "1px dashed var(--line)", color: "rgba(255,255,255,0.65)" }}>
          Loading audit...
        </div>
      ) : rows.length === 0 ? (
        <div className="rounded-xl px-4 py-5 text-sm" style={{ border: "1px dashed var(--line)", color: "rgba(255,255,255,0.65)" }}>
          No audit records found.
        </div>
      ) : rows.length === 0 ? (
        <div className="rounded-xl px-4 py-5 text-sm" style={{ border: "1px dashed var(--line)", color: "rgba(255,255,255,0.65)" }}>
          No audit records match the current filters.
        </div>
      ) : (
        <div className="space-y-2">
          {rows.map((row) => (
            <div key={row.id} className="rounded-xl p-3" style={{ border: "1px solid rgba(255,255,255,0.08)", background: "rgba(255,255,255,0.015)" }}>
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="rounded-full px-2 py-0.5 text-[10px] font-semibold" style={{ border: "1px solid rgba(0,212,255,0.2)", color: "rgba(0,212,255,0.9)" }}>
                    {row.action}
                  </span>
                  <span className="text-xs" style={{ color: "rgba(255,255,255,0.65)" }}>
                    {formatDateTime(row.createdAt)}
                  </span>
                </div>
                <div className="flex flex-wrap gap-2 text-[11px]">
                  <span style={{ color: row.activeAfter ? "#86efac" : "#fca5a5" }}>
                    {row.activeAfter ? "Active" : "Inactive"}
                  </span>
                  <span style={{ color: row.deletedAfter ? "#fde68a" : "rgba(255,255,255,0.55)" }}>
                    {row.deletedAfter ? "Deleted" : "Not Deleted"}
                  </span>
                </div>
              </div>

              <div className="mt-2 grid gap-1 text-xs">
                <p style={{ color: "rgba(255,255,255,0.7)" }}>
                  <span style={{ color: "rgba(255,255,255,0.5)" }}>Actor:</span>{" "}
                  {row.actorSub || "system"}{" "}
                  {row.actorType ? `(${row.actorType})` : ""}
                </p>
                {row.email && (
                  <p style={{ color: "rgba(255,255,255,0.7)" }}>
                    <span style={{ color: "rgba(255,255,255,0.5)" }}>Email:</span> {row.email}
                  </p>
                )}
                {row.reason && (
                  <p style={{ color: "rgba(255,255,255,0.8)" }}>
                    <span style={{ color: "rgba(255,255,255,0.5)" }}>Reason:</span> {row.reason}
                  </p>
                )}
                {row.permissions && row.permissions.length > 0 && (
                  <div className="flex flex-wrap gap-2 pt-1">
                    {row.permissions.map((permission) => (
                      <span
                        key={`${row.id}:${permission}`}
                        className="rounded-full px-2 py-0.5 text-[10px]"
                        style={{ border: "1px solid rgba(0,212,255,0.15)", color: "rgba(0,212,255,0.85)" }}
                      >
                        {permission}
                      </span>
                    ))}
                  </div>
                )}
              </div>
            </div>
          ))}
          <div className="flex flex-wrap items-center justify-between gap-2 rounded-xl px-3 py-2" style={{ border: "1px solid rgba(255,255,255,0.08)" }}>
            <label className="flex items-center gap-2 text-xs" style={{ color: "rgba(255,255,255,0.7)" }}>
              <span>Page Size</span>
              <select
                value={pageSize}
                onChange={(e) => {
                  setPageSize(Number(e.target.value));
                  setPage(0);
                }}
                className="rounded-lg px-2 py-1"
                style={{ background: "var(--surface-2)", border: "1px solid var(--line)", color: "var(--ink)" }}
              >
                {[10, 20, 50, 100].map((sizeOpt) => (
                  <option key={sizeOpt} value={sizeOpt}>{sizeOpt}</option>
                ))}
              </select>
            </label>
            <div className="flex items-center gap-2">
              <button className="btn-ghost" disabled={!hasPrevPage || loading} onClick={() => setPage((p) => Math.max(0, p - 1))}>
                Prev
              </button>
              <span className="text-xs" style={{ color: "rgba(255,255,255,0.7)" }}>
                {totalPages === 0 ? "No pages" : `Page ${page + 1} of ${totalPages}`}
              </span>
              <button className="btn-ghost" disabled={!hasNextPage || loading} onClick={() => setPage((p) => p + 1)}>
                Next
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}
