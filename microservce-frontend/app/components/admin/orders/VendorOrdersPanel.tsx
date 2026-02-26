"use client";

import { getAllowedNextStatuses, canTransitionOrderStatus, getStatusChip, ORDER_STATUSES } from "./orderStatus";
import { VendorOrder } from "./types";

type Props = {
  orderId: string | null;
  loading: boolean;
  rows: VendorOrder[];
  statusDrafts: Record<string, string>;
  statusSavingId: string | null;
  activeHistoryVendorOrderId: string | null;
  historyLoading: boolean;
  onDraftChange: (vendorOrderId: string, status: string) => void;
  onToggleHistory: (vendorOrderId: string) => void | Promise<void>;
  onSaveStatus: (vendorOrderId: string) => void | Promise<void>;
  onRefresh: () => void | Promise<void>;
  onClose: () => void;
};

export default function VendorOrdersPanel({
  orderId,
  loading,
  rows,
  statusDrafts,
  statusSavingId,
  activeHistoryVendorOrderId,
  historyLoading,
  onDraftChange,
  onToggleHistory,
  onSaveStatus,
  onRefresh,
  onClose,
}: Props) {
  if (!orderId) return null;

  return (
    <div className="mb-3 rounded-xl border border-[rgba(124,58,237,0.15)] bg-[rgba(124,58,237,0.04)] p-3">
      <div className="flex items-center justify-between gap-2.5">
        <div>
          <p className="m-0 text-base font-bold text-[#ddd6fe]">Vendor Orders</p>
          <p className="mt-1 font-mono text-[0.7rem] text-muted">{orderId}</p>
        </div>
        <div className="flex gap-2">
          <button
            type="button"
            onClick={() => { void onRefresh(); }}
            disabled={loading}
            className="rounded-md border border-[rgba(124,58,237,0.18)] bg-[rgba(124,58,237,0.08)] px-2.5 py-1.5 text-xs text-[#c4b5fd]"
          >
            {loading ? "Refreshing..." : "Refresh"}
          </button>
          <button
            type="button"
            onClick={onClose}
            className="rounded-md border border-white/[0.08] bg-white/[0.02] px-2.5 py-1.5 text-xs text-muted"
          >
            Close
          </button>
        </div>
      </div>

      <div className="mt-2.5 grid gap-2">
        {loading && <div className="text-xs text-muted">Loading vendor orders...</div>}
        {!loading && rows.length === 0 && (
          <div className="text-xs text-muted">No vendor orders found for this order.</div>
        )}
        {!loading && rows.map((row) => {
          const chip = getStatusChip(row.status || "PENDING");
          const draftStatus = statusDrafts[row.id] || row.status || "PENDING";
          const canSaveVendorOrder =
            draftStatus !== (row.status || "PENDING") && canTransitionOrderStatus(row.status, draftStatus);
          return (
            <div
              key={row.id}
              className="rounded-md border border-white/[0.06] bg-white/[0.015] p-2.5"
            >
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="grid gap-[3px]">
                  <span className="font-mono text-xs text-[#c8c8e8]">{row.id}</span>
                  <span className="font-mono text-[0.68rem] text-muted">vendor: {row.vendorId}</span>
                </div>
                <span
                  className="rounded-full px-2.5 py-0.5 text-[0.7rem] font-extrabold"
                  style={{
                    background: chip.bg,
                    border: `1px solid ${chip.border}`,
                    color: chip.color,
                  }}
                >
                  {(row.status || "PENDING").replaceAll("_", " ")}
                </span>
              </div>
              <div className="mt-1.5 flex flex-wrap gap-2.5 text-[0.7rem] text-muted">
                <span>Qty: {row.quantity}</span>
                <span>Items: {row.itemCount}</span>
                <span>Total: ${Number(row.orderTotal ?? 0).toFixed(2)}</span>
                <span>{new Date(row.createdAt).toLocaleString()}</span>
              </div>
              <div className="mt-2 flex flex-wrap items-center gap-2">
                <select
                  value={draftStatus}
                  onChange={(e) => onDraftChange(row.id, e.target.value)}
                  disabled={loading || statusSavingId === row.id}
                  className="min-w-[170px] rounded-md border border-[rgba(124,58,237,0.18)] bg-[rgba(124,58,237,0.06)] px-2.5 py-2 text-[0.74rem] text-[#e9d5ff]"
                >
                  {ORDER_STATUSES.map((s) => (
                    <option key={s} value={s} disabled={!getAllowedNextStatuses(row.status).includes(s)}>
                      {s.replaceAll("_", " ")}
                    </option>
                  ))}
                </select>
                <button
                  type="button"
                  onClick={() => { void onToggleHistory(row.id); }}
                  disabled={loading || statusSavingId !== null}
                  className={`rounded-md border border-white/[0.08] px-2.5 py-2 text-xs font-bold ${
                    activeHistoryVendorOrderId === row.id ? "bg-white/5 text-[#c8c8e8]" : "bg-white/[0.02] text-muted"
                  }`}
                >
                  {historyLoading && activeHistoryVendorOrderId === row.id ? "Loading..." : activeHistoryVendorOrderId === row.id ? "Hide History" : "History"}
                </button>
                <button
                  type="button"
                  onClick={() => { void onSaveStatus(row.id); }}
                  disabled={loading || statusSavingId !== null || !canSaveVendorOrder}
                  title={
                    canTransitionOrderStatus(row.status, draftStatus)
                      ? "Save vendor-order status"
                      : `Invalid transition: ${row.status} -> ${draftStatus}`
                  }
                  className={`rounded-md border border-[rgba(124,58,237,0.18)] px-2.5 py-2 text-xs font-bold text-[#ddd6fe] ${
                    statusSavingId === row.id ? "bg-[rgba(124,58,237,0.12)]" : "bg-[rgba(124,58,237,0.08)]"
                  } ${canSaveVendorOrder ? "opacity-100" : "opacity-55"}`}
                >
                  {statusSavingId === row.id ? "Saving..." : "Save"}
                </button>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
