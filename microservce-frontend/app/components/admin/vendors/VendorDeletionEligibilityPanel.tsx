"use client";

import type { Vendor, VendorDeletionEligibility } from "./types";

type Props = {
  vendor: Vendor | null;
  eligibility?: VendorDeletionEligibility | null;
  loading?: boolean;
  onRefresh: () => void;
};

export default function VendorDeletionEligibilityPanel({
  vendor,
  eligibility = null,
  loading = false,
  onRefresh,
}: Props) {
  return (
    <section className="card-surface rounded-2xl p-5">
      <div className="mb-3 flex items-center justify-between gap-2">
        <div>
          <h2 className="text-xl text-[var(--ink)]">Deletion Eligibility</h2>
          <p className="mt-1 text-xs text-[var(--muted)]">
            Shows whether the selected vendor can be soft deleted now and why it may be blocked.
          </p>
        </div>
        <button
          type="button"
          onClick={onRefresh}
          disabled={!vendor || loading}
          className="rounded-md border border-[var(--line)] px-2 py-1 text-xs disabled:cursor-not-allowed disabled:opacity-60"
          style={{ background: "var(--surface-2)", color: "var(--ink-light)" }}
        >
          {loading ? "Checking..." : "Refresh"}
        </button>
      </div>

      {!vendor && (
        <div className="rounded-xl border border-[var(--line)] p-4 text-sm text-[var(--muted)]">
          Select a vendor to view deletion eligibility.
        </div>
      )}

      {vendor && (
        <div className="space-y-3 rounded-xl border border-[var(--line)] p-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div>
              <p className="font-semibold text-[var(--ink)]">{vendor.name}</p>
              <p className="text-xs text-[var(--muted)]">
                {vendor.slug} | {vendor.id}
              </p>
            </div>
            <span
              className="rounded px-2 py-1 text-[11px] font-semibold"
              style={{
                border: `1px solid ${eligibility?.eligible ? "rgba(16,185,129,0.25)" : "rgba(239,68,68,0.25)"}`,
                background: eligibility?.eligible ? "rgba(16,185,129,0.08)" : "rgba(239,68,68,0.08)",
                color: eligibility?.eligible ? "#34d399" : "#fca5a5",
              }}
            >
              {eligibility ? (eligibility.eligible ? "Eligible" : "Blocked") : (loading ? "Checking..." : "Not loaded")}
            </span>
          </div>

          <div className="grid gap-2 text-xs text-[var(--muted)] md:grid-cols-2">
            <div className="rounded-lg border border-[var(--line)] p-3">
              <p className="text-[var(--ink-light)]">Total Orders</p>
              <p className="mt-1 text-base font-semibold text-[var(--ink)]">{eligibility?.totalOrders ?? "-"}</p>
            </div>
            <div className="rounded-lg border border-[var(--line)] p-3">
              <p className="text-[var(--ink-light)]">Pending / Open Orders</p>
              <p className="mt-1 text-base font-semibold text-[var(--ink)]">{eligibility?.pendingOrders ?? "-"}</p>
            </div>
          </div>

          <div className="grid gap-2 text-xs text-[var(--muted)] md:grid-cols-2">
            <div className="rounded-lg border border-[var(--line)] p-3">
              <p className="text-[var(--ink-light)]">Last Order</p>
              <p className="mt-1 text-[var(--ink)]">
                {eligibility?.lastOrderAt ? new Date(eligibility.lastOrderAt).toLocaleString() : "No orders"}
              </p>
            </div>
            <div className="rounded-lg border border-[var(--line)] p-3">
              <p className="text-[var(--ink-light)]">Refund Hold Until</p>
              <p className="mt-1 text-[var(--ink)]">
                {eligibility?.refundHoldUntil ? new Date(eligibility.refundHoldUntil).toLocaleString() : "No hold window"}
              </p>
            </div>
          </div>

          {eligibility && !eligibility.eligible && (
            <div
              className="rounded-lg border p-3 text-xs"
              style={{ borderColor: "rgba(239,68,68,0.25)", background: "rgba(239,68,68,0.05)" }}
            >
              <p className="font-semibold text-[var(--ink)]">Blocking reasons</p>
              <ul className="mt-2 list-disc space-y-1 pl-4 text-[var(--muted)]">
                {(eligibility.blockingReasons || []).length > 0 ? (
                  eligibility.blockingReasons.map((reason) => <li key={reason}>{reason}</li>)
                ) : (
                  <li>Deletion blocked</li>
                )}
              </ul>
            </div>
          )}
        </div>
      )}
    </section>
  );
}
