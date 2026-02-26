"use client";

import type { Vendor, VendorLifecycleAudit } from "./types";

type Props = {
  vendor: Vendor | null;
  audits: VendorLifecycleAudit[];
  loading?: boolean;
  onRefresh: () => void;
};

function actionTone(action: string) {
  if (action.includes("DELETE")) return { bg: "rgba(239,68,68,0.08)", color: "#fca5a5", border: "rgba(239,68,68,0.25)" };
  if (action.includes("RESTORE") || action.includes("RESUME")) return { bg: "rgba(16,185,129,0.08)", color: "#34d399", border: "rgba(16,185,129,0.25)" };
  if (action.includes("STOP")) return { bg: "rgba(251,191,36,0.08)", color: "#fde68a", border: "rgba(251,191,36,0.25)" };
  return { bg: "var(--surface-2)", color: "var(--ink-light)", border: "var(--line)" };
}

export default function VendorLifecycleAuditPanel({ vendor, audits, loading = false, onRefresh }: Props) {
  return (
    <section className="card-surface rounded-2xl p-5">
      <div className="mb-3 flex items-center justify-between gap-2">
        <div>
          <h2 className="text-xl text-[var(--ink)]">Lifecycle Audit</h2>
          <p className="mt-1 text-xs text-[var(--muted)]">
            Tracks vendor lifecycle actions such as stop/resume orders, delete request, confirm delete, and restore.
          </p>
        </div>
        <button
          type="button"
          onClick={onRefresh}
          disabled={!vendor || loading}
          className="rounded-md border border-[var(--line)] bg-surface-2 px-2 py-1 text-xs text-ink-light disabled:cursor-not-allowed disabled:opacity-60"
        >
          {loading ? "Refreshing..." : "Refresh"}
        </button>
      </div>

      {!vendor && (
        <div className="rounded-xl border border-[var(--line)] p-4 text-sm text-[var(--muted)]">
          Select a vendor from the Vendors table using the `Manage` button to view lifecycle audit history.
        </div>
      )}

      {vendor && audits.length === 0 && !loading && (
        <div className="rounded-xl border border-[var(--line)] p-4 text-sm text-[var(--muted)]">
          No lifecycle audit entries found for this vendor.
        </div>
      )}

      {vendor && (
        <div className="space-y-2">
          {loading && audits.length === 0 && (
            <div className="rounded-xl border border-[var(--line)] p-4 text-sm text-[var(--muted)]">Loading audit...</div>
          )}
          {audits.map((audit) => {
            const tone = actionTone(audit.action);
            return (
              <div key={audit.id} className="rounded-xl border border-[var(--line)] p-3">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <span
                        className="rounded px-2 py-1 text-[11px] font-semibold"
                        style={{ background: tone.bg, color: tone.color, border: `1px solid ${tone.border}` }}
                      >
                        {audit.action}
                      </span>
                      <span className="text-xs text-[var(--muted)]">
                        {new Date(audit.createdAt).toLocaleString()}
                      </span>
                    </div>
                    <p className="mt-1 text-xs text-[var(--muted)]">
                      {audit.actorType || "UNKNOWN"} | {audit.changeSource || "UNKNOWN"} |{" "}
                      {audit.actorSub?.trim() ? audit.actorSub : "system"}
                    </p>
                    {audit.reason?.trim() && (
                      <p className="mt-2 rounded-md border border-[var(--line)] p-2 text-xs text-[var(--ink-light)]">
                        Reason: {audit.reason}
                      </p>
                    )}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </section>
  );
}
